package com.example.JobScheduler.auth.Services;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.entity.User;
import com.example.JobScheduler.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerDetailService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Check Redis cache first
        String cachedPassword = redisTemplate.opsForValue().get("user:" + email);
        if (cachedPassword != null) {
            log.debug("Cache HIT for user: {}", email);
            return new org.springframework.security.core.userdetails.User(
                email, cachedPassword, List.of(new SimpleGrantedAuthority("USER"))
            );
        }

        log.debug("Cache MISS for user: {}", email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not Found"));

        // Cache for 5 minutes
        redisTemplate.opsForValue().set("user:" + email, user.getPassword(),
            Duration.ofMinutes(5));

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPassword(),
            List.of(new SimpleGrantedAuthority("USER"))
        );
    }
}
