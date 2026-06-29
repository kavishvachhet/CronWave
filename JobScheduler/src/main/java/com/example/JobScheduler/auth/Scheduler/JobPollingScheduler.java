package com.example.JobScheduler.auth.Scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.JobScheduler.auth.Services.JobClaimService;
import com.example.JobScheduler.auth.dto.JobExecutionEvent;
import com.example.JobScheduler.auth.entity.Job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobPollingScheduler {
    
    private final JobClaimService jobClaimService;
    private final KafkaTemplate<String, JobExecutionEvent> kafkaTemplate;

    private static final String TOPIC = "job-executions";

    @Scheduled(fixedDelay = 5000)
    public void polljobs(){
        System.out.println("Polling at : " + LocalDateTime.now());

        List<Job> claimedJobs = jobClaimService.claimDueJobs();

        if(claimedJobs.isEmpty()){
            return;
        }

        log.info("Claimed {} jobs at {}", claimedJobs.size(), LocalDateTime.now());

        for(Job job : claimedJobs){
            // Convert Job entity → lightweight Kafka event
            JobExecutionEvent event = JobExecutionEvent.builder()
                .jobId(job.getId())
                .name(job.getName())
                .url(job.getUrl())
                .method(job.getMethod())
                .requestBody(job.getRequestBody())
                .cronExpression(job.getCronExpression())
                .attempt(1)
                .maxRetries(job.getMaxRetries())
                .build();

            kafkaTemplate.send(TOPIC, job.getId(), event);
            log.info("📤 Published job {} to Kafka topic '{}'", job.getName(), TOPIC);
        }
    }
}

