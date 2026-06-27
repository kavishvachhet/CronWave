package com.example.JobScheduler.auth.dto;

import lombok.Data;

@Data
public class CreateJobRequest {
    
    private String name;

    private String userId;

    private String url;

    private String method;

    private String requestBody;

    private String cronExpression;
}
