package com.example.JobScheduler.auth.Services;

import java.util.List;

import com.example.JobScheduler.auth.entity.Job;

public interface JobClaimService {
    List<Job> claimDueJobs();
}
