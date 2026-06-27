package com.example.JobScheduler.auth.Scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.JobScheduler.auth.Services.JobClaimService;
import com.example.JobScheduler.auth.Services.JobExecutorService;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.JobRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobPollingScheduler {
    
    private final JobRepo jobRepo;
    private final JobExecutorService jobExecutorService;
    private final JobClaimService jobClaimService;

    // @Scheduled(fixedDelay = 5000)
    // public void polljobs(){
    //     System.out.println("Polling at : " + LocalDateTime.now());
    //     List<Job> duejobs = jobRepo.findByStatusAndNextRunAtLessThanEqual(jobstatus.ACTIVE, LocalDateTime.now());

    //     if(duejobs.isEmpty()) return;
    //     log.info("Found {} due jobs at {}", duejobs.size(), LocalDateTime.now());
    //     // System.out.println("Found jobs: " + duejobs.size());
    //     for(Job job : duejobs){
    //         System.out.println("Executing Job : " + job.getName());
    //         job.setStatus(jobstatus.RUNNING);
    //         jobRepo.save(job);
    //         jobExecutorService.executeAsync(job);
    //     }
    // }

    @Scheduled(fixedDelay = 5000)
    public void polljobs(){
        System.out.println("Polling at : " + LocalDateTime.now());

        List<Job> claimDueJobs = jobClaimService.claimDueJobs();

        if(claimDueJobs.isEmpty()){
            return;
        }
        log.info("Claimed {} jobs at {}", claimDueJobs.size(), LocalDateTime.now());
        for(Job job : claimDueJobs){
            jobExecutorService.executeAsync(job);
        }
    }
}
