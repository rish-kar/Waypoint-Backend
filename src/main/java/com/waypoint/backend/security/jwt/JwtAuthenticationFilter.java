package com.waypoint.backend.security.jwt;

import com.waypoint.backend.model.common.ApiErrorResponse;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.equals("/api/v1/auth/google")
                || path.equals("/api/v1/auth/microsoft/start")
                || path.equals("/api/v1/auth/microsoft/callback")
                || path.equals("/api/v1/auth/session/exchange")
                || path.equals("/api/v1/webhooks/lemonsqueezy")
                || path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) { filterChain.doFilter(request, response); return; }
        String token = extractBearerToken(authorization);
        if (token == null) { reject(request, response, "invalid_authorization_header"); return; }
        try {
            JwtClaims claims = jwtService.parseToken(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            LOGGER.atDebug().addKeyValue("event", "bearer_token_accepted").addKeyValue("user_id", claims.userId())
                    .addKeyValue("method", request.getMethod()).addKeyValue("path", request.getRequestURI()).log("Bearer token accepted");
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, "invalid_or_expired_token");
        }
    }

    private String extractBearerToken(String authorization) {
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"Bearer".equalsIgnoreCase(authorization.substring(0, separator))) return null;
        String token = authorization.substring(separator + 1);
        if (token.isBlank() || !token.equals(token.trim()) || token.chars().anyMatch(Character::isWhitespace)) return null;
        return token;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) throws IOException {
        LOGGER.atWarn().addKeyValue("event", "bearer_token_rejected").addKeyValue("reason", reason)
                .addKeyValue("method", request.getMethod()).addKeyValue("path", request.getRequestURI()).log("Bearer token rejected");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Invalid or expired bearer token", request.getRequestURI()));
    }
}
