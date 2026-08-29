package com.waypoint.backend.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WaypointApiMetricsFilter extends OncePerRequestFilter {
    public static final String REQUEST_COUNTER = "waypoint.api.requests";
    public static final String ERROR_COUNTER = "waypoint.api.errors";
    public static final String REQUEST_DURATION = "waypoint.api.request.duration";

    private static final Set<String> KNOWN_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");

    private final MeterRegistry meterRegistry;

    public WaypointApiMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            int effectiveStatus = failed && response.getStatus() < 400
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();
            record(request, effectiveStatus, System.nanoTime() - startedAt);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    private void record(HttpServletRequest request, int status, long durationNanos) {
        String area = area(request.getRequestURI());
        String method = method(request.getMethod());
        String outcome = outcome(status);

        Counter.builder(REQUEST_COUNTER)
                .description("Waypoint API requests grouped by bounded product area and outcome")
                .tag("area", area)
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        if (status >= 400) {
            Counter.builder(ERROR_COUNTER)
                    .description("Waypoint API client and server errors")
                    .tag("area", area)
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        }

        Timer.builder(REQUEST_DURATION)
                .description("Waypoint API request duration by bounded product area and outcome")
                .tag("area", area)
                .tag("method", method)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String area(String path) {
        if (path.startsWith("/api/v1/auth/")) return "auth";
        if (path.startsWith("/api/v1/ai/")) return "ai";
        if (path.startsWith("/api/v1/billing/")) return "billing";
        if (path.startsWith("/api/v1/webhooks/")) return "webhook";
        if (path.startsWith("/api/v1/admin/")) return "admin";
        if (path.startsWith("/api/v1/account")) return "account";
        if (path.startsWith("/api/v1/entitlements")) return "entitlement";
        if (path.startsWith("/api/v1/subscriptions")) return "subscription";
        return "other";
    }

    private String method(String value) {
        if (value == null) return "OTHER";
        String normalized = value.toUpperCase(Locale.ROOT);
        return KNOWN_METHODS.contains(normalized) ? normalized : "OTHER";
    }

    private String outcome(int status) {
        if (status >= 200 && status < 300) return "success";
        if (status >= 300 && status < 400) return "redirect";
        if (status >= 400 && status < 500) return "client_error";
        if (status >= 500) return "server_error";
        return "other";
    }
}
