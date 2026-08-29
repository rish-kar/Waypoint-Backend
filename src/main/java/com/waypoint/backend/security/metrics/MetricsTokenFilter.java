package com.waypoint.backend.security.metrics;

import com.waypoint.backend.model.common.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

public final class MetricsTokenFilter extends OncePerRequestFilter {
    private static final String BEARER_SCHEME = "Bearer";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public MetricsTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (suppliedToken == null || !constantTimeEquals(suppliedToken)) {
            unauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !BEARER_SCHEME.equalsIgnoreCase(authorization.substring(0, separator))) {
            return null;
        }
        String token = authorization.substring(separator + 1).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean constantTimeEquals(String suppliedToken) {
        byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"waypoint-metrics\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Metrics authentication required",
                request.getRequestURI()
        ));
    }
}
