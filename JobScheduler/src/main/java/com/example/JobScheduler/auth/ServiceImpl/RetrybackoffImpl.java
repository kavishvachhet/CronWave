package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.JobScheduler.auth.Enum.ExecutionStatus;
import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.Services.RetryService;
import com.example.JobScheduler.auth.entity.ExecutionLog;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.ExecutionRepo;
import com.example.JobScheduler.auth.repository.JobRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class RetrybackoffImpl implements RetryService {

    private final JobRepo jobRepo;
    private final ExecutionRepo executionRepo;
    private final RestTemplate restTemplate;
    private final CronSerivce cronSerivce;

    @Async("jobExecutorPool")
    @Override
    public void retrywithbackoff(Job job) {
        int maxRetries = job.getMaxRetries();

        while (job.getRetrycount() < maxRetries) {
            int currentAttempt = job.getRetrycount()+1;

            long waitMs = (long) Math.pow(2,currentAttempt) *1000;

            log.info("Job {} retry attempt {}/{} — waiting {}ms",
                job.getName(), currentAttempt, maxRetries, waitMs);

            job.setStatus(jobstatus.RETRYING);
            job.setRetrycount(currentAttempt);
            jobRepo.save(job);

            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long startTime = System.currentTimeMillis();

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");
                HttpEntity<String> entity = buildEntity(job, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                    job.getUrl(),
                    HttpMethod.valueOf(job.getMethod()),
                    entity,
                    String.class
                );

                long responseTime = System.currentTimeMillis() - startTime;

                String body = response.getBody();
                if (body != null && body.length() > 5000) {
                    body = body.substring(0, 5000) + "...[truncated]";
                }

                
                saveLog(job.getId(), ExecutionStatus.SUCCESS, currentAttempt,
                        response.getStatusCode().value(), responseTime, null, body);

                LocalDateTime nextRun = cronSerivce.getNextExecution(
                    job.getCronExpression(), LocalDateTime.now()
                );

                job.setStatus(jobstatus.ACTIVE);
                job.setNextRunAt(nextRun);
                job.setRetrycount(0);   
                jobRepo.save(job);

                log.info("Job {} succeeded on retry attempt {}. Next run: {}",
                    job.getName(), currentAttempt, nextRun);

                return; 

            } catch (Exception e) {
                long responseTime = System.currentTimeMillis() - startTime;

                log.error("Job {} retry attempt {} failed: {}",
                    job.getName(), currentAttempt, e.getMessage());

                
                saveLog(job.getId(), ExecutionStatus.FAILED, currentAttempt,
                        0, responseTime, e.getMessage(), null);

                
            }

        }

        log.error("Job {} exhausted all {} retries. Marking DEAD.",
            job.getName(), maxRetries);

        job.setStatus(jobstatus.DEAD);
        jobRepo.save(job);

        
        

        }


    private HttpEntity<String> buildEntity(Job job, HttpHeaders headers) {
        String method = job.getMethod().toUpperCase();
        if (method.equals("GET") || method.equals("DELETE")) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(job.getRequestBody(), headers);
    }



    private void saveLog(String jobId, ExecutionStatus status, int attempt,
                         int responseCode, long responseTimeMs,
                         String error, String body) {
        ExecutionLog log = ExecutionLog.builder()
            .jobId(jobId)
            .status(status)
            .attempt(attempt)
            .responseCode(responseCode)
            .responseBody(body)
            .responseTimeMs(responseTimeMs)
            .error(error)
            .executedAt(LocalDateTime.now())
            .build();

        executionRepo.save(log);
    }
    
}
