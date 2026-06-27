package com.example.JobScheduler.auth.ServiceImpl;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.entity.Job;
import com.example.JobScheduler.auth.repository.JobRepo;
import com.example.JobScheduler.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobServiceCacheImpl {
    private final UserRepository userRepository;
    private final JobRepo jobRepo;
    @Cacheable(value = "jobs", key = "#email + '-' + #page + '-' + #size")
    public Page<Job> getMyJobsCached(String email, int page, int size) {
        log.info("CACHE MISS — hitting MongoDB for: {}", email);

        var user = userRepository
            .findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        return jobRepo.findByUserId(user.getId(), pageable);
    }
}
