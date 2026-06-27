package com.example.JobScheduler.auth.dto;

import com.example.JobScheduler.auth.entity.jobstatus;

import lombok.Data;

@Data
public class UpdateJobStatus {
    private jobstatus status;
}
