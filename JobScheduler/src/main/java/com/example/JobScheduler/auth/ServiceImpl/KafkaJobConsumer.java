package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDateTime;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.JobScheduler.auth.Enum.ExecutionStatus;
import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.dto.JobExecutionEvent;
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
public class KafkaJobConsumer {

    private final JobRepo jobRepo;
    private final ExecutionRepo executionRepo;
    private final CronSerivce cronSerivce;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, JobExecutionEvent> kafkaTemplate;

    private static final String TOPIC = "job-executions";

    @KafkaListener(topics = TOPIC, groupId = "cronwave-workers")
    public void consumeJobExecution(JobExecutionEvent event) {
        log.info(" Consuming job event: {} | attempt {}/{} | url: {}",
            event.getName(), event.getAttempt(), event.getMaxRetries(), event.getUrl());

        
        Job currentJob = jobRepo.findById(event.getJobId()).orElse(null);
        if (currentJob == null) {
            log.warn(" Job {} was deleted. Cancelling execution.", event.getJobId());
            return;
        }
        
        if (currentJob.getStatus() == jobstatus.PAUSED) {
            log.info(" Job {} is PAUSED. Cancelling execution.", event.getJobId());
            return;
        }
        
        if (currentJob.getStatus() == jobstatus.DEAD) {
            log.info(" Job {} is DEAD. Cancelling execution.", event.getJobId());
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<String> entity = buildEntity(event, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                event.getUrl(),
                HttpMethod.valueOf(event.getMethod()),
                entity,
                String.class
            );

            long responseTime = System.currentTimeMillis() - startTime;

            
            String body = response.getBody();
            if (body != null && body.length() > 5000) {
                body = body.substring(0, 5000) + "...[truncated]";
            }

            
            saveLog(event.getJobId(), ExecutionStatus.SUCCESS, event.getAttempt(),
                    response.getStatusCode().value(), responseTime, null, body);

            
            Job job = jobRepo.findById(event.getJobId()).orElse(null);
            if (job != null) {
                LocalDateTime nextRun = cronSerivce.getNextExecution(
                    event.getCronExpression(), LocalDateTime.now()
                );
                job.setStatus(jobstatus.ACTIVE);
                job.setNextRunAt(nextRun);
                job.setRetrycount(0);
                jobRepo.save(job);

                log.info(" Job {} succeeded. Next run at: {}", event.getName(), nextRun);
            }

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;

            log.error(" Job {} attempt {}/{} failed: {}",
                event.getName(), event.getAttempt(), event.getMaxRetries(), e.getMessage());

            
            saveLog(event.getJobId(), ExecutionStatus.FAILED, event.getAttempt(),
                    0, responseTime, e.getMessage(), null);

            
            if (event.getAttempt() < event.getMaxRetries()) {
                retryViaKafka(event);
            } else {
                markJobDead(event);
            }
        }
    }

    
    private void retryViaKafka(JobExecutionEvent event) {
        int nextAttempt = event.getAttempt() + 1;
        long delayMs = (long) Math.pow(2, nextAttempt) * 1000;

        log.info(" Scheduling retry {}/{} for job {} in {}ms",
            nextAttempt, event.getMaxRetries(), event.getName(), delayMs);

        
        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            job.setStatus(jobstatus.RETRYING);
            job.setRetrycount(nextAttempt);
            jobRepo.save(job);
        });

        
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        
        JobExecutionEvent retryEvent = JobExecutionEvent.builder()
            .jobId(event.getJobId())
            .name(event.getName())
            .url(event.getUrl())
            .method(event.getMethod())
            .requestBody(event.getRequestBody())
            .cronExpression(event.getCronExpression())
            .attempt(nextAttempt)
            .maxRetries(event.getMaxRetries())
            .build();

        kafkaTemplate.send(TOPIC, event.getJobId(), retryEvent);
        log.info(" Re-published retry event for job {} (attempt {})", event.getName(), nextAttempt);
    }

    
    private void markJobDead(JobExecutionEvent event) {
        log.error(" Job {} exhausted all {} retries. Marking DEAD.",
            event.getName(), event.getMaxRetries());

        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            job.setStatus(jobstatus.DEAD);
            jobRepo.save(job);
        });
    }

    

    private HttpEntity<String> buildEntity(JobExecutionEvent event, HttpHeaders headers) {
        String method = event.getMethod().toUpperCase();
        if (method.equals("GET") || method.equals("DELETE")) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(event.getRequestBody(), headers);
    }

    private void saveLog(String jobId, ExecutionStatus status, int attempt,
                         int responseCode, long responseTimeMs,
                         String error, String body) {
        ExecutionLog executionLog = ExecutionLog.builder()
            .jobId(jobId)
            .status(status)
            .attempt(attempt)
            .responseCode(responseCode)
            .responseBody(body)
            .responseTimeMs(responseTimeMs)
            .error(error)
            .executedAt(LocalDateTime.now())
            .build();

        executionRepo.save(executionLog);
    }
}
