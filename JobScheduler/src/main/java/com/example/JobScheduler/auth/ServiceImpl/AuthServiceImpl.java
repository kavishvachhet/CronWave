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
        
        if(userRepository.findByEmail(req.getEmail()).isPresent()){
            return new AuthResponse("Email Already Exists");
        }

        
        String userId = UUID.randomUUID().toString();

        
        String token = jwtService.generateToken(req.getEmail(), userId);

        
        registrationWorker.processRegistration(userId, req.getName(), req.getEmail(), req.getPassword());

        
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

