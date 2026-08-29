package com.waypoint.backend.controller.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleAuthRequest;
import com.waypoint.backend.security.jwt.JwtClaims;
import com.waypoint.backend.security.jwt.JwtRevocationService;
import com.waypoint.backend.service.auth.GoogleAuthService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final GoogleAuthService googleAuthService;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(GoogleAuthService googleAuthService, JwtRevocationService jwtRevocationService) {
        this.googleAuthService = googleAuthService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.login(request.accessToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof JwtClaims claims)) {
            throw new UnauthorizedException("Authentication required");
        }
        jwtRevocationService.revoke(claims);
    }
}
