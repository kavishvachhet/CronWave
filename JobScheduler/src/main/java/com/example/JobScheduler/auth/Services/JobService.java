package com.example.JobScheduler.auth.Services;

import java.util.List;

import org.springframework.data.domain.Page;

import com.example.JobScheduler.auth.dto.CreateJobRequest;
import com.example.JobScheduler.auth.dto.UpdateJobStatus;
import com.example.JobScheduler.auth.entity.Job;

public interface JobService {
    String createJob(CreateJobRequest req);
    Page<Job> getMyJobs(int page, int size);
    String DeleteJob(String jobid);
    String updateJobStatus(String jobid,UpdateJobStatus request);
}
