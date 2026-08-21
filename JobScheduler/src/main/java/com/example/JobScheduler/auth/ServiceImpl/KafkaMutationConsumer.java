package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDateTime;

import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.dto.JobMutationEvent;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.JobRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMutationConsumer {

    private final JobRepo jobRepo;
    private final CronSerivce cronSerivce;
    private final CacheManager cacheManager;

    @KafkaListener(topics = "job-mutations", groupId = "cronwave-mutations", containerFactory = "mutationKafkaListenerContainerFactory")
    public void consumeMutation(JobMutationEvent event) {
        log.info(" Consuming mutation event: {} for user: {}", event.getMutationType(), event.getUserId());

        try {
            switch (event.getMutationType()) {
                case CREATE:
                    handleCreate(event);
                    break;
                case UPDATE_STATUS:
                    handleUpdateStatus(event);
                    break;
                case DELETE:
                    handleDelete(event);
                    break;
                default:
                    log.warn("Unknown mutation type: {}", event.getMutationType());
                    return;
            }

            
            evictCache();

        } catch (Exception e) {
            log.error(" Failed to process mutation event: {}", e.getMessage(), e);
            
        }
    }

    private void handleCreate(JobMutationEvent event) {
        LocalDateTime nextRun = cronSerivce.getNextExecution(event.getCronExpression(), LocalDateTime.now());

        Job job = Job.builder()
            .userId(event.getUserId())
            .name(event.getName())
            .url(event.getUrl())
            .method(event.getMethod())
            .requestBody(event.getRequestBody())
            .cronExpression(event.getCronExpression())
            .status(jobstatus.ACTIVE)
            .nextRunAt(nextRun)
            .build();

        jobRepo.save(job);
        log.info(" Asynchronously created job: {}", job.getName());
    }

    private void handleUpdateStatus(JobMutationEvent event) {
        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            
            if (job.getUserId().equals(event.getUserId())) {
                job.setStatus(event.getStatus());
                jobRepo.save(job);
                log.info(" Asynchronously updated job {} status to {}", job.getId(), event.getStatus());
            } else {
                log.warn("Unauthorized attempt to update job {}", job.getId());
            }
        });
    }

    private void handleDelete(JobMutationEvent event) {
        jobRepo.findById(event.getJobId()).ifPresent(job -> {
            
            if (job.getUserId().equals(event.getUserId())) {
                jobRepo.delete(job);
                log.info(" Asynchronously deleted job {}", job.getId());
            } else {
                log.warn("Unauthorized attempt to delete job {}", job.getId());
            }
        });
    }

    private void evictCache() {
        if (cacheManager.getCache("jobs") != null) {
            cacheManager.getCache("jobs").clear();
            log.debug(" Redis cache 'jobs' evicted.");
        }
    }
}
