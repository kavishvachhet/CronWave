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

/**
 * Kafka consumer that executes jobs received from the "job-executions" topic.
 * 
 * Flow:
 *   1. Scheduler claims jobs → publishes JobExecutionEvent to Kafka
 *   2. This consumer picks up events → executes the HTTP call
 *   3. On success → logs result, reschedules job to ACTIVE
 *   4. On failure → re-publishes with attempt+1 (exponential backoff delay)
 *   5. On retries exhausted → marks job DEAD
 */
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
        log.info("▶ Consuming job event: {} | attempt {}/{} | url: {}",
            event.getName(), event.getAttempt(), event.getMaxRetries(), event.getUrl());

        long startTime = System.currentTimeMillis();

        try {
            // Build and execute the HTTP request
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

            // Truncate large response bodies
            String body = response.getBody();
            if (body != null && body.length() > 5000) {
                body = body.substring(0, 5000) + "...[truncated]";
            }

            // Log success
            saveLog(event.getJobId(), ExecutionStatus.SUCCESS, event.getAttempt(),
                    response.getStatusCode().value(), responseTime, null, body);

            // Reschedule: calculate next cron run, set back to ACTIVE
            Job job = jobRepo.findById(event.getJobId()).orElse(null);
            if (job != null) {
                LocalDateTime nextRun = cronSerivce.getNextExecution(
                    event.getCronExpression(), LocalDateTime.now()
                );
                job.setStatus(jobstatus.ACTIVE);
                job.setNextRunAt(nextRun);
                job.setRetrycount(0);
                jobRepo.save(job);

                log.info("✅ Job {} succeeded. Next run at: {}", event.getName(), nextRun);
            }

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;

            log.error("❌ Job {} attempt {}/{} failed: {}",
                event.getName(), event.getAttempt(), event.getMaxRetries(), e.getMessage());

            // Log the failure
            saveLog(event.getJobId(), ExecutionStatus.FAILED, event.getAttempt(),
                    0, responseTime, e.getMessage(), null);

            // Retry or mark DEAD
            if (event.getAttempt() < event.getMaxRetries()) {
                retryViaKafka(event);
            } else {
                markJobDead(event);
            }
        }
    }

    /**
     * Re-publish the event to Kafka with incremented attempt and exponential backoff delay.
     * Delay: 2^attempt seconds (2s, 4s, 8s, 16s, 32s)
     */
    private void retryViaKafka(JobExecutionEvent event) {
        int nextAttempt = event.getAttempt() + 1;
        long delayMs = (long) Math.pow(2, nextAttempt) * 1000;

        log.info("🔄 Scheduling retry {}/{} for job {} in {}ms",
            nextAttempt, event.getMaxRetries(), event.getName(), delayMs);

        // Update job status to RETRYING in MongoDB
        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            job.setStatus(jobstatus.RETRYING);
            job.setRetrycount(nextAttempt);
            jobRepo.save(job);
        });

        // Brief delay before re-publishing (non-blocking from scheduler's perspective)
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        // Re-publish with incremented attempt
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
        log.info("📤 Re-published retry event for job {} (attempt {})", event.getName(), nextAttempt);
    }

    /**
     * All retries exhausted — mark the job as DEAD.
     */
    private void markJobDead(JobExecutionEvent event) {
        log.error("💀 Job {} exhausted all {} retries. Marking DEAD.",
            event.getName(), event.getMaxRetries());

        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            job.setStatus(jobstatus.DEAD);
            jobRepo.save(job);
        });
    }

    // ─── Helpers ─────────────────────────────────────────────

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
