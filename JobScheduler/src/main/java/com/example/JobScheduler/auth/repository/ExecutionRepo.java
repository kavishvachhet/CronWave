package com.example.JobScheduler.auth.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.JobScheduler.auth.entity.ExecutionLog;


public interface ExecutionRepo extends MongoRepository<ExecutionLog,String>{
    List<ExecutionLog> findByJobIdOrderByExecutedAtDesc(String jobId);
}
