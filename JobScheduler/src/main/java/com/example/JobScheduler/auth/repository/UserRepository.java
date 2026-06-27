package com.example.JobScheduler.auth.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.JobScheduler.auth.entity.User;

public interface UserRepository extends MongoRepository<User,String>{
    Optional<User> findByEmail(String email);

}
