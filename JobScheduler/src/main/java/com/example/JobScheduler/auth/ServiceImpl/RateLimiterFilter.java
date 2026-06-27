package com.example.JobScheduler.auth.ServiceImpl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

    // private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final int REGISTER_LIMIT = 5;
    private static final int LOGIN_LIMIT     = 10;
    private static final int JOBS_LIMIT      = 60;
    private static final Duration WINDOW     = Duration.ofMinutes(1);

    // Atomic Lua script — increment + set expiry in one Redis operation
    // No race condition possible
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +  // ADD tonumber()
            "end " +
            "return count"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!shouldLimit(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip    = getClientIP(request);
        int limit    = getLimit(path);
        String key   = "rate:" + ip + ":" + path;

        // Execute atomic Lua script — increment and set expiry together
        Long count = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            List.of(key),
            String.valueOf(WINDOW.getSeconds())
        );

        if (count == null) {
            // Redis is down — fail open (let request through) or fail closed
            // Failing open here so Redis outage doesn't bring down your API
            log.error("Redis unavailable — skipping rate limit for IP: {}", ip);
            filterChain.doFilter(request, response);
            return;
        }

        if (count > limit) {
            Long ttl = redisTemplate.getExpire(key);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : WINDOW.getSeconds();

            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{" +
                "\"error\": \"Too many requests\"," +
                "\"retryAfter\": " + retryAfter +
                "}"
            );

            log.warn("Rate limit exceeded — IP: {} | path: {} | count: {}/{}", ip, path, count, limit);
            return;
        }

        // Attach rate limit headers to every passing response
        response.addHeader("X-Rate-Limit-Limit",     String.valueOf(limit));
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(Math.max(0, limit - count)));

        filterChain.doFilter(request, response);
    }

    private int getLimit(String path) {
        if (path.startsWith("/auth/register")) return REGISTER_LIMIT;
        if (path.startsWith("/auth/login"))    return LOGIN_LIMIT;
        return JOBS_LIMIT;
    }

    private boolean shouldLimit(String path) {
        return path.startsWith("/auth/") || path.startsWith("/jobs");
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For can be comma-separated — take first (real client IP)
        return ip.split(",")[0].trim();
    }
}