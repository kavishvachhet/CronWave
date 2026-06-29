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
    @Cacheable(value = "jobs", key = "#userId + '-' + #page + '-' + #size", sync = true)
    public com.example.JobScheduler.auth.dto.JobPageResponse getMyJobsCached(String userId, int page, int size) {
        log.info("CACHE MISS — hitting MongoDB for userId: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Job> pageResult = jobRepo.findByUserId(userId, pageable);
        return com.example.JobScheduler.auth.dto.JobPageResponse.fromPage(pageResult);
    }
}
