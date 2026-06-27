package com.example.JobScheduler.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.JobScheduler.auth.ServiceImpl.AuthServiceImpl;
import com.example.JobScheduler.auth.Services.AuthService;
import com.example.JobScheduler.auth.dto.AuthResponse;
import com.example.JobScheduler.auth.dto.LoginRequest;
import com.example.JobScheduler.auth.dto.RegisterRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest req){
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req){
        return  ResponseEntity.ok(authService.login(req));
    }
}
