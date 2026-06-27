package com.example.JobScheduler.auth.ServiceImpl;


import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.JobScheduler.auth.Enum.ExecutionStatus;
import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.Services.JobExecutorService;
import com.example.JobScheduler.auth.Services.RetryService;
import com.example.JobScheduler.auth.entity.ExecutionLog;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.ExecutionRepo;
import com.example.JobScheduler.auth.repository.JobRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutorServiceImpl implements JobExecutorService {

    private final JobRepo jobRepo;
    private final ExecutionRepo executionRepo;
    private final CronSerivce cronSerivce;
    private final RestTemplate restTemplate;
    private final RetryService retryService;

    @Async("jobExecutorPool")   // fix typo
    @Override
    public void executeAsync(Job job) {
        log.info("Executing job: {} | method: {} | url: {}",
            job.getName(), job.getMethod(), job.getUrl());

        long startTime = System.currentTimeMillis();  // fix typo
        int attempt = 1;                               // fix typo

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

            saveLog(job.getId(), ExecutionStatus.SUCCESS, attempt,
                    response.getStatusCode().value(), responseTime, null, body);

            LocalDateTime nextRun = cronSerivce.getNextExecution(
                job.getCronExpression(), LocalDateTime.now()
            );

            job.setStatus(jobstatus.ACTIVE);
            job.setNextRunAt(nextRun);
            jobRepo.save(job);

            log.info("Job {} succeeded. Next run at: {}", job.getName(), nextRun);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;

            log.error("Job {} failed: {}", job.getName(), e.getMessage());

            // saveLog(job.getId(), ExecutionStatus.FAILED, attempt,
            //         0, responseTime, e.getMessage(), null); 

            job.setStatus(jobstatus.FAILED);
            jobRepo.save(job);
            retryService.retrywithbackoff(job); 
        }
    }

    private HttpEntity<String> buildEntity(Job job, HttpHeaders headers) {
        String method = job.getMethod().toUpperCase();

        if (method.equals("GET") || method.equals("DELETE")) {
            return new HttpEntity<>(headers);
        }

        return new HttpEntity<>(job.getRequestBody(), headers);
    }

    private void saveLog(String id, ExecutionStatus status, int attempt,
                         int responseCode, long responseTimeMs,
                         String error, String body) {
        ExecutionLog log = ExecutionLog.builder()
            .jobId(id)
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