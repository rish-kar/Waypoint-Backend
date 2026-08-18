package com.waypoint.backend.controller.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleAuthRequest;
import com.waypoint.backend.model.auth.MicrosoftAuthStartRequest;
import com.waypoint.backend.model.auth.MicrosoftAuthStartResponse;
import com.waypoint.backend.model.auth.SessionExchangeRequest;
import com.waypoint.backend.model.auth.SessionResponse;
import com.waypoint.backend.service.auth.GoogleAuthService;
import com.waypoint.backend.service.auth.MicrosoftOAuthService;
import com.waypoint.backend.service.auth.WaypointSessionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final GoogleAuthService googleAuthService;
    private final MicrosoftOAuthService microsoftOAuthService;
    private final WaypointSessionService sessionService;

    public AuthController(GoogleAuthService googleAuthService, MicrosoftOAuthService microsoftOAuthService,
                          WaypointSessionService sessionService) {
        this.googleAuthService = googleAuthService;
        this.microsoftOAuthService = microsoftOAuthService;
        this.sessionService = sessionService;
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.login(request.accessToken());
    }

    @PostMapping("/microsoft/start")
    public MicrosoftAuthStartResponse microsoftStart(@Valid @RequestBody MicrosoftAuthStartRequest request) {
        return microsoftOAuthService.start(request.redirectUri());
    }

    @GetMapping("/microsoft/callback")
    public ResponseEntity<Void> microsoftCallback(@RequestParam(required = false) String code,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(required = false) String error) {
        URI redirect = microsoftOAuthService.callback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirect.toASCIIString()).build();
    }

    @PostMapping("/session/exchange")
    public AuthResponse exchange(@Valid @RequestBody SessionExchangeRequest request) {
        return microsoftOAuthService.exchange(request.exchangeCode());
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal UUID userId) {
        return sessionService.current(userId);
    }

    @PostMapping("/session/refresh")
    public AuthResponse refresh(@AuthenticationPrincipal UUID userId) {
        return sessionService.issue(userId);
    }
}
