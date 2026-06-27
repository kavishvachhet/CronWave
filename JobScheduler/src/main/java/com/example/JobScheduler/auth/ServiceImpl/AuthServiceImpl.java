package com.example.JobScheduler.auth.ServiceImpl;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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


    @Override
    public String register(RegisterRequest req) {
        if(userRepository.findByEmail(req.getEmail()).isPresent()){
            return "Email Already Exists";
        }

        User user = User.builder().name(req.getName()).email(req.getEmail()).
        password(passwordEncoder.encode(req.getPassword())).build();

        userRepository.save(user);
        return "User Registered SuccessFully";
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

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token);
    }
}
