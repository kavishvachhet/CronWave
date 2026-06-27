package com.example.JobScheduler.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
