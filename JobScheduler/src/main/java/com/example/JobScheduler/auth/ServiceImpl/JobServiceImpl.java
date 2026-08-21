package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDateTime;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.Services.JobService;
import com.example.JobScheduler.auth.Utils.UserPrincipal;
import com.example.JobScheduler.auth.dto.CreateJobRequest;
import com.example.JobScheduler.auth.dto.JobMutationEvent;
import com.example.JobScheduler.auth.dto.UpdateJobStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {
    
    private final CronSerivce cronSerivce;
    private final JobServiceCacheImpl jobServiceCacheImpl;
    private final KafkaTemplate<String, JobMutationEvent> kafkaTemplate;

    private static final String TOPIC = "job-mutations";

    @Override
    public String createJob(CreateJobRequest req) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal.getUserId();

        
        try {
            cronSerivce.getNextExecution(req.getCronExpression(), LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression", e);
        }

        JobMutationEvent event = JobMutationEvent.builder()
            .mutationType(JobMutationEvent.MutationType.CREATE)
            .userId(userId)
            .name(req.getName())
            .url(req.getUrl())
            .method(req.getMethod())
            .requestBody(req.getRequestBody())
            .cronExpression(req.getCronExpression())
            .build();

        kafkaTemplate.send(TOPIC, userId, event);
        return "Job creation accepted and queued"; 
    }

    @Override
    public String DeleteJob(String jobid) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal.getUserId();

        JobMutationEvent event = JobMutationEvent.builder()
            .mutationType(JobMutationEvent.MutationType.DELETE)
            .userId(userId)
            .jobId(jobid)
            .build();

        kafkaTemplate.send(TOPIC, userId, event);
        return "Job deletion accepted and queued";
    }

    @Override
    public String updateJobStatus(String jobid, UpdateJobStatus request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal.getUserId();

        JobMutationEvent event = JobMutationEvent.builder()
            .mutationType(JobMutationEvent.MutationType.UPDATE_STATUS)
            .userId(userId)
            .jobId(jobid)
            .status(request.getStatus())
            .build();

        kafkaTemplate.send(TOPIC, userId, event);
        return "Job status update accepted and queued";
    }

    @Override
    public com.example.JobScheduler.auth.dto.JobPageResponse getMyJobs(int page, int size) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal.getUserId();

        
        return jobServiceCacheImpl.getMyJobsCached(userId, page, size);
    }
}

