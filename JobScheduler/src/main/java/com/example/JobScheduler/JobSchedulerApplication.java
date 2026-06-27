package com.example.JobScheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableCaching
public class JobSchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobSchedulerApplication.class, args);
	}

}
