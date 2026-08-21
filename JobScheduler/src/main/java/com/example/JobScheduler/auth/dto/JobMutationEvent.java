package com.example.JobScheduler.auth.dto;

import com.example.JobScheduler.auth.entity.jobstatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMutationEvent {

    public enum MutationType {
        CREATE,
        UPDATE_STATUS,
        DELETE
    }

    private MutationType mutationType;
    private String userId;
    
    
    private String jobId;
    private jobstatus status;

    
    private String name;
    private String url;
    private String method;
    private String requestBody;
    private String cronExpression;
}
