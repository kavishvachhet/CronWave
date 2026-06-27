package com.example.JobScheduler.auth.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import ch.qos.logback.classic.spi.Configurator.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "execution_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionLog {
    @Id
    private String id;

    @Indexed
    private String jobId;



    private int attempt;

    private com.example.JobScheduler.auth.Enum.ExecutionStatus status;

    private int responseCode;

    private long responseTimeMs;

    private String responseBody;

    private String error;   

    private LocalDateTime executedAt;
}
