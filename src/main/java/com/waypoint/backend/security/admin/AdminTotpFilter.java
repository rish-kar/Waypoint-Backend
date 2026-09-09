package com.waypoint.backend.security.admin;

import com.waypoint.backend.config.admin.AdminProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class AdminTotpFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Admin-TOTP";

    private final AdminProperties adminProperties;
    private final AdminTotpVerifier adminTotpVerifier;

    public AdminTotpFilter(AdminProperties adminProperties, AdminTotpVerifier adminTotpVerifier) {
        this.adminProperties = adminProperties;
        this.adminTotpVerifier = adminTotpVerifier;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        String suppliedCode = request.getHeader(HEADER);

        boolean configuredAdmin = StringUtils.hasText(username)
                && username.equals(adminProperties.id());
        boolean validCode = configuredAdmin
                && StringUtils.hasText(adminProperties.totpSecret())
                && adminTotpVerifier.validCode(adminProperties.totpSecret(), suppliedCode, Instant.now());

        if (!validCode) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"ADMIN_MFA_REQUIRED\",\"message\":\"Valid admin MFA code required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
