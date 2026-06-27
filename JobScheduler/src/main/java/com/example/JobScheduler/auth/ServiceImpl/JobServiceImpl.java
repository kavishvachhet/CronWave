package com.example.JobScheduler.auth.ServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.coyote.BadRequestException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.CronSerivce;
import com.example.JobScheduler.auth.Services.JobService;
import com.example.JobScheduler.auth.dto.CreateJobRequest;
import com.example.JobScheduler.auth.dto.UpdateJobStatus;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.User;
import com.example.JobScheduler.auth.entity.jobstatus;
import com.example.JobScheduler.auth.repository.JobRepo;
import com.example.JobScheduler.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {
    private final JobRepo jobRepo;
    private final UserRepository userRepository;
    private final CronSerivce cronSerivce;
    private final JobServiceCacheImpl jobServiceCacheImpl;

    @CacheEvict(value = "jobs", allEntries = true)
    @Override
    public String createJob(CreateJobRequest req) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        
        User user = userRepository
        .findByEmail(email)
        .orElseThrow(() ->
                new RuntimeException("User not found"));

        LocalDateTime nexRun;

        try {
            nexRun = cronSerivce.getNextExecution(
                    req.getCronExpression(),
                    LocalDateTime.now()
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression", e);
        }
        
        Job job = Job.builder()
        .userId(user.getId())
        .name(req.getName())
        .url(req.getUrl())
        .method(req.getMethod())
        .requestBody(req.getRequestBody())
        .cronExpression(req.getCronExpression())
        .status(jobstatus.ACTIVE)
        .nextRunAt(nexRun)
        .build();

        jobRepo.save(job);
        return "Job Created"; 
    }

    // @Cacheable(value = "jobs", key = "#email + '-' + #page + '-' + #size")
    // public Page<Job> getMyJobsCached(String email, int page, int size) {
    //     //  log.info("CACHE MISS — hitting MongoDB for: {}", email);
    //     User user = userRepository
    //         .findByEmail(email)
    //         .orElseThrow(() -> new RuntimeException("User not found"));

    //     Pageable pageable = PageRequest.of(page, size);
    //     return jobRepo.findByUserId(user.getId(), pageable);
    // }
    // @Override
    // public List<Job> getMyJobs(){
    //     String email = SecurityContextHolder
    //         .getContext()
    //         .getAuthentication()
    //         .getName();

    //     User user = userRepository
    //             .findByEmail(email)
    //             .orElseThrow();

    //     Pageable
    //     return jobRepo.findByUserId(user.getId());

    // }

    @CacheEvict(value = "jobs", allEntries = true)
    @Override
    public String DeleteJob(String jobid) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email).orElseThrow(()->
        new RuntimeException("User Not Found"));

        Job job = jobRepo.findById(jobid).orElseThrow(()-> new RuntimeException("Job not Found"));
        
        if(!job.getUserId().equals(user.getId())){
            throw new RuntimeException("UnAuthorized");
        }

        jobRepo.delete(job);
        return "Job Deleted SuccessFully";

    }

    @Override
    public String updateJobStatus(String jobid, UpdateJobStatus request) {
        String email = SecurityContextHolder.getContext().
                    getAuthentication().getName();
        
        User user = userRepository.findByEmail(email).orElseThrow(()->
            new RuntimeException("User Not Found"));

        Job job = jobRepo.findById(jobid).orElseThrow(()->
            new RuntimeException("Job Not Found"));

        if (!job.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        job.setStatus(request.getStatus());
        jobRepo.save(job);
        return "Job status updated successfully";
    }

    // @Cacheable(value = "jobs",key = "#root.authentication.name + #page + #size")
    @Override
    public Page<Job> getMyJobs(int page, int size) {

        String email = SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getName();

         return jobServiceCacheImpl.getMyJobsCached(email, page, size);
    }


}
