package com.example.JobScheduler.auth.Services;

import com.example.JobScheduler.auth.entity.Job;

public interface JobExecutorService {
    void executeAsync(Job job);
}