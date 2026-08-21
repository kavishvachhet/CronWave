package com.example.JobScheduler.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class Healthcheck {
    
    @GetMapping("/check")
    public String test(){
        return "Working";
    }

}
