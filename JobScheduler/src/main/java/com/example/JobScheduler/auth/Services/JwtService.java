package com.example.JobScheduler.auth.Services;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email, String userId){
        String compact = Jwts.builder().subject(email)
        .claim("userId", userId)
        .issuedAt(new Date()).expiration(new 
            Date(System.currentTimeMillis()+ 1000*60*60*24)).signWith(key).compact();
        return compact;
    }

    private Claims extractClaims(String token){
        Claims payload = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return payload;
    }
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractUserId(String token) {
        return extractClaims(token).get("userId", String.class);
    }

    public boolean isValid(String token){
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
