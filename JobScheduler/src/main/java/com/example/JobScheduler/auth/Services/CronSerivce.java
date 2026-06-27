package com.example.JobScheduler.auth.Services;

import java.time.LocalDateTime;


public interface CronSerivce {
    LocalDateTime getNextExecution(String cronexp,LocalDateTime fromTime) throws IllegalArgumentException;
}
