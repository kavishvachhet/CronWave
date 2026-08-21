package com.example.JobScheduler.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "jobExecutorPool")
    public Executor jobExecutorPool(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("job-exec-");
        executor.initialize();
        
        return executor;
    }

    @Bean(name = "registrationExecutorPool")
    public Executor registrationExecutorPool(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);       
        executor.setMaxPoolSize(8);        
        executor.setQueueCapacity(10000);  
        executor.setThreadNamePrefix("reg-worker-");
        executor.initialize();
        return executor;
    }
    
}
