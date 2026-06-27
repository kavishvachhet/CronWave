package com.example.JobScheduler.auth.Services;

import com.example.JobScheduler.auth.entity.Job;

public interface RetryService {
    void retrywithbackoff(Job job);
}
