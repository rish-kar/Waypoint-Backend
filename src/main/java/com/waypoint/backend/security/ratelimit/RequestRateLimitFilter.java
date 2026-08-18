package com.waypoint.backend.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int DEFAULT_LIMIT = 600;
    private static final int AUTH_LIMIT = 30;
    private static final int ADMIN_LIMIT = 120;
    private static final int WEBHOOK_LIMIT = 120;

    private final DistributedRateLimiter rateLimiter;

    public RequestRateLimitFilter(DistributedRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String bucket = bucket(request.getRequestURI());
        int limit = limit(bucket);
        String key = request.getRemoteAddr() + ':' + bucket;

        if (!rateLimiter.allow(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String bucket(String path) {
        if (path.startsWith("/api/v1/admin/")) {
            return "admin";
        }
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if ("/api/v1/webhooks/lemonsqueezy".equals(path)) {
            return "webhook";
        }
        return "default";
    }

    private int limit(String bucket) {
        return switch (bucket) {
            case "auth" -> AUTH_LIMIT;
            case "admin" -> ADMIN_LIMIT;
            case "webhook" -> WEBHOOK_LIMIT;
            default -> DEFAULT_LIMIT;
        };
    }
}
