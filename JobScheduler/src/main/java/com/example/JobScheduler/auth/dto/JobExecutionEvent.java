package com.example.JobScheduler.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight event published to Kafka for job execution.
 * Only carries the data needed to execute — not the full Job entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionEvent {

    private String jobId;
    private String name;
    private String url;
    private String method;
    private String requestBody;
    private String cronExpression;

    @Builder.Default
    private int attempt = 1;

    @Builder.Default
    private int maxRetries = 5;
}
