package com.waypoint.backend.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int DEFAULT_LIMIT = 600;
    private static final int AUTH_LIMIT = 30;
    private static final int ADMIN_LIMIT = 120;
    private static final int WEBHOOK_LIMIT = 120;
    private static final int MAX_TRACKED_WINDOWS = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String bucket = bucket(request.getRequestURI());
        int limit = limit(bucket);
        String key = request.getRemoteAddr() + ':' + bucket;
        AtomicBoolean allowed = new AtomicBoolean();

        windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                allowed.set(true);
                return new Window(now, 1);
            }
            if (current.count() >= limit) {
                allowed.set(false);
                return current;
            }
            allowed.set(true);
            return new Window(current.startedAt(), current.count() + 1);
        });

        if (windows.size() > MAX_TRACKED_WINDOWS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }

        if (!allowed.get()) {
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
        if ("/api/v1/auth/google".equals(path)) {
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

    private record Window(long startedAt, int count) {
    }
}
