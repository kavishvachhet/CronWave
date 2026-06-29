package com.example.JobScheduler.auth.ServiceImpl;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.JobScheduler.auth.Services.AuthService;
import com.example.JobScheduler.auth.Services.JwtService;
import com.example.JobScheduler.auth.dto.AuthResponse;
import com.example.JobScheduler.auth.dto.LoginRequest;
import com.example.JobScheduler.auth.dto.RegisterRequest;
import com.example.JobScheduler.auth.entity.User;
import com.example.JobScheduler.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistrationWorker registrationWorker;


    @Override
    public AuthResponse register(RegisterRequest req) {
        // Quick check — this is a fast indexed query
        if(userRepository.findByEmail(req.getEmail()).isPresent()){
            return new AuthResponse("Email Already Exists");
        }

        // Generate userId upfront so the JWT is valid immediately
        String userId = UUID.randomUUID().toString();

        // Generate JWT instantly (no CPU-heavy work here)
        String token = jwtService.generateToken(req.getEmail(), userId);

        // Offload the SLOW password hashing + MongoDB save to background
        registrationWorker.processRegistration(userId, req.getName(), req.getEmail(), req.getPassword());

        // Return the token immediately — user can start using the API right now!
        return new AuthResponse(token);
    }

    @Override
    public AuthResponse login(LoginRequest req) {

        User user = userRepository.findByEmail(req.getEmail()).orElse(null);

        if (user == null) {
            return new AuthResponse("Invalid Email Or Password");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return new AuthResponse("Invalid Email Or Password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token);
    }
}

