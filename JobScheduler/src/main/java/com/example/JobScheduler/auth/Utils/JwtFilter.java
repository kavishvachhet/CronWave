package com.example.JobScheduler.auth.Utils;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.JobScheduler.auth.Services.CustomerDetailService;
import com.example.JobScheduler.auth.Services.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomerDetailService userdetailservice;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // TODO Auto-generated method stub
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email = jwtService.extractEmail(token);

        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userdetails = userdetailservice.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken authtoken = 
                new UsernamePasswordAuthenticationToken(userdetails,null,userdetails.getAuthorities());
            
            authtoken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authtoken);

        }
        filterChain.doFilter(request, response);
        // throw new UnsupportedOperationException("Unimplemented method 'doFilterInternal'");
    }
    
}
