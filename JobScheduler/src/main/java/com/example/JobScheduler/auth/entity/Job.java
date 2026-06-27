package com.example.JobScheduler.auth.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// import com.example.JobScheduler.auth.enum.JobStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "jobs")
@CompoundIndex(def = "{'status': 1, 'nextRunAt': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    
    private String url;

    private String method;

    private String requestBody;

    private String cronExpression;

    private LocalDateTime nextRunAt;

    private jobstatus status;

    @Builder.Default
    private int retrycount = 0;

    @Builder.Default
    private int maxRetries = 5;
}
