package com.example.JobScheduler.auth.ServiceImpl;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.RecoveryService;
import com.example.JobScheduler.auth.dto.JobExecutionEvent;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.JobRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
@RequiredArgsConstructor
public class RecoveryServiceImpl implements RecoveryService {

    private final JobRepo jobRepo;
    private final KafkaTemplate<String, JobExecutionEvent> kafkaTemplate;

    private static final String TOPIC = "job-executions";

    @EventListener(ApplicationReadyEvent.class) 
    @Override
    public void recoverStuckJobs() {
        
        List<Job> stuckJobs = jobRepo.findByStatusIn(List.of(jobstatus.RETRYING,jobstatus.RUNNING));

        if(stuckJobs.isEmpty()){
            log.info("Recovery: no stuck jobs found.");
            return;
        }

        log.warn("Recovery: found {} stuck jobs. Recovering via Kafka...", stuckJobs.size());

        for(Job job : stuckJobs){
            if(job.getRetrycount()>=job.getMaxRetries()){
                log.warn("Recovery: job {} exceeded max retries. Marking DEAD.", job.getName());
                job.setStatus(jobstatus.DEAD);
                jobRepo.save(job);
            }else{
                log.warn("Recovery: re-publishing job {} to Kafka (attempt {}/{})",
                    job.getName(), job.getRetrycount() + 1, job.getMaxRetries());

                job.setStatus(jobstatus.RETRYING);
                jobRepo.save(job);

                
                JobExecutionEvent event = JobExecutionEvent.builder()
                    .jobId(job.getId())
                    .name(job.getName())
                    .url(job.getUrl())
                    .method(job.getMethod())
                    .requestBody(job.getRequestBody())
                    .cronExpression(job.getCronExpression())
                    .attempt(job.getRetrycount() + 1)
                    .maxRetries(job.getMaxRetries())
                    .build();

                kafkaTemplate.send(TOPIC, job.getId(), event);
            }
        }
    }
    
}

