package com.example.JobScheduler.auth.ServiceImpl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.entity.User;
import com.example.JobScheduler.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationWorker {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Async("registrationExecutorPool")
    public void processRegistration(String userId, String name, String email, String rawPassword) {
        log.info("Background registration started for: {}", email);

        // This is the SLOW part (~200ms of CPU time for BCrypt)
        String hashedPassword = passwordEncoder.encode(rawPassword);

        User user = User.builder()
            .id(userId)
            .name(name)
            .email(email)
            .password(hashedPassword)
            .build();

        userRepository.save(user);
        log.info("Background registration completed for: {}", email);
    }
}
