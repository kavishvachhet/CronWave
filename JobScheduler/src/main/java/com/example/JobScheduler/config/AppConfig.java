package com.example.JobScheduler.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

@Configuration
public class AppConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CronParser cronParser(){
        return new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory){
        return new MongoTemplate(factory);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder){
        return builder.connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(30))
        .build();
    }
}
