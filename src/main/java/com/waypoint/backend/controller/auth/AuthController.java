package com.waypoint.backend.controller.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleAuthRequest;
import com.waypoint.backend.model.auth.SessionRefreshRequest;
import com.waypoint.backend.security.jwt.JwtClaims;
import com.waypoint.backend.security.jwt.JwtRevocationService;
import com.waypoint.backend.service.auth.GoogleAuthService;
import com.waypoint.backend.service.auth.GoogleOAuthWebService;
import com.waypoint.backend.service.auth.WaypointSessionService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final GoogleAuthService googleAuthService;
    private final GoogleOAuthWebService googleOAuthWebService;
    private final WaypointSessionService sessionService;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(
            GoogleAuthService googleAuthService,
            GoogleOAuthWebService googleOAuthWebService,
            WaypointSessionService sessionService,
            JwtRevocationService jwtRevocationService
    ) {
        this.googleAuthService = googleAuthService;
        this.googleOAuthWebService = googleOAuthWebService;
        this.sessionService = sessionService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.login(request.accessToken());
    }

    @GetMapping("/google/start")
    public ResponseEntity<Void> googleStart(@RequestParam String returnUrl) {
        URI location = googleOAuthWebService.authorizationUri(returnUrl);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        URI location = googleOAuthWebService.callbackUri(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @PostMapping("/session/refresh")
    public AuthResponse refresh(@Valid @RequestBody SessionRefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof JwtClaims claims)) {
            throw new UnauthorizedException("Authentication required");
        }
        jwtRevocationService.revoke(claims);
        sessionService.revokeAll(claims.userId());
    }
}
