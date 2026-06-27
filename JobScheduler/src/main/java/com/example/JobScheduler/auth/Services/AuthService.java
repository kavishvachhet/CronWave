package com.example.JobScheduler.auth.Services;

import com.example.JobScheduler.auth.dto.AuthResponse;
import com.example.JobScheduler.auth.dto.LoginRequest;
import com.example.JobScheduler.auth.dto.RegisterRequest;

public interface AuthService {
    String register(RegisterRequest req);

    AuthResponse login(LoginRequest req);
}
