package com.example.JobScheduler.auth.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.entity.jobstatus;


public interface JobRepo extends MongoRepository<Job,String>{
    List<Job> findByUserId(String userId);
    List<Job> findByStatusAndNextRunAtLessThanEqual(
        jobstatus status,
        LocalDateTime time);
    List<Job> findByStatusIn(List<jobstatus> statuses);
    Page<Job> findByUserId(String userId,Pageable pageable);
    
}

