package com.waypoint.backend.security.admin;

import com.waypoint.backend.service.admin.AdminAccountService;

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

public class AdminTotpFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Admin-TOTP";

    private final AdminAccountService adminAccountService;

    public AdminTotpFilter(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
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
        if (!StringUtils.hasText(username) || !adminAccountService.validTotp(username, suppliedCode)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"ADMIN_MFA_REQUIRED\",\"message\":\"Valid admin MFA code required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}