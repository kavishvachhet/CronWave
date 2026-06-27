package com.example.JobScheduler.auth.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.JobScheduler.auth.ServiceImpl.JobServiceImpl;
import com.example.JobScheduler.auth.Services.JobService;
import com.example.JobScheduler.auth.dto.CreateJobRequest;
import com.example.JobScheduler.auth.dto.UpdateJobStatus;
import com.example.JobScheduler.auth.entity.ExecutionLog;
import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.repository.ExecutionRepo;
import com.mongodb.internal.bulk.UpdateRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor

public class JobController {
    private final JobService jobService;
    private final ExecutionRepo executionRepo;
    
    @PostMapping
    public ResponseEntity<String> createJob(@RequestBody CreateJobRequest req){
        return ResponseEntity.ok(jobService.createJob(req));
    }

    @GetMapping
    public ResponseEntity<Page<Job>> getMyJobs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(jobService.getMyJobs(page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteJob(@PathVariable String id){
        return ResponseEntity.ok(jobService.DeleteJob(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable String id,
        @RequestBody UpdateJobStatus status){
        return ResponseEntity.ok(jobService.updateJobStatus(id, status));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ExecutionLog>> getLogs(@PathVariable String id){
        return ResponseEntity.ok(
            executionRepo.findByJobIdOrderByExecutedAtDesc(id)
        );
    }
    
}
