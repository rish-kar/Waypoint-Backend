package com.waypoint.backend.controller.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleAuthRequest;
import com.waypoint.backend.model.auth.GoogleAuthStartResponse;
import com.waypoint.backend.model.auth.MicrosoftAuthStartRequest;
import com.waypoint.backend.model.auth.MicrosoftAuthStartResponse;
import com.waypoint.backend.model.auth.SessionExchangeRequest;
import com.waypoint.backend.model.auth.SessionRefreshRequest;
import com.waypoint.backend.model.auth.SessionResponse;
import com.waypoint.backend.security.jwt.JwtClaims;
import com.waypoint.backend.security.jwt.JwtRevocationService;
import com.waypoint.backend.service.auth.GoogleAuthService;
import com.waypoint.backend.service.auth.GoogleOAuthWebService;
import com.waypoint.backend.service.auth.MicrosoftCredentialService;
import com.waypoint.backend.service.auth.MicrosoftOAuthService;
import com.waypoint.backend.service.auth.WaypointSessionService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final GoogleAuthService googleAuthService;
    private final GoogleOAuthWebService googleOAuthWebService;
    private final MicrosoftOAuthService microsoftOAuthService;
    private final MicrosoftCredentialService microsoftCredentialService;
    private final WaypointSessionService sessionService;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(
            GoogleAuthService googleAuthService,
            GoogleOAuthWebService googleOAuthWebService,
            MicrosoftOAuthService microsoftOAuthService,
            MicrosoftCredentialService microsoftCredentialService,
            WaypointSessionService sessionService,
            JwtRevocationService jwtRevocationService
    ) {
        this.googleAuthService = googleAuthService;
        this.googleOAuthWebService = googleOAuthWebService;
        this.microsoftOAuthService = microsoftOAuthService;
        this.microsoftCredentialService = microsoftCredentialService;
        this.sessionService = sessionService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.login(request.accessToken());
    }

    @GetMapping("/google/start")
    public ResponseEntity<?> googleStart(@RequestParam(required = false) String returnUrl) {
        if (!StringUtils.hasText(returnUrl)) {
            GoogleAuthStartResponse response = googleOAuthWebService.startManualLogin();
            return ResponseEntity.ok(response);
        }
        URI location = googleOAuthWebService.authorizationUri(returnUrl);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<?> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        GoogleOAuthWebService.CallbackResult result = googleOAuthWebService.callback(code, state, error);
        if (!result.manual()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(result.redirectUri()).build();
        }
        if (result.success()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Google sign-in complete. Return to Postman and run Complete Login.");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Google sign-in failed. Return to Postman and start Google login again.");
    }

    @PostMapping("/microsoft/start")
    public MicrosoftAuthStartResponse microsoftStart(@Valid @RequestBody MicrosoftAuthStartRequest request) {
        return microsoftOAuthService.start(request.redirectUri());
    }

    @PostMapping("/microsoft/link/start")
    public MicrosoftAuthStartResponse microsoftLinkStart(
            @Valid @RequestBody MicrosoftAuthStartRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return microsoftOAuthService.startLink(request.redirectUri(), userId);
    }

    @GetMapping("/microsoft/callback")
    public ResponseEntity<Void> microsoftCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        URI redirect = microsoftOAuthService.callback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect.toASCIIString())
                .build();
    }

    @PostMapping("/session/exchange")
    public AuthResponse exchange(@Valid @RequestBody SessionExchangeRequest request) {
        if (googleOAuthWebService.supportsManualExchangeCode(request.exchangeCode())) {
            return googleOAuthWebService.exchangeManualLogin(request.exchangeCode());
        }
        return microsoftOAuthService.exchange(request.exchangeCode());
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal UUID userId) {
        return sessionService.current(userId);
    }

    @PostMapping("/session/refresh")
    public AuthResponse refresh(@Valid @RequestBody SessionRefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @DeleteMapping("/microsoft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectMicrosoft(@AuthenticationPrincipal UUID userId) {
        if (userId == null) throw new UnauthorizedException("Authentication required");
        microsoftCredentialService.disconnect(userId);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        JwtClaims claims = requireClaims(authentication);
        jwtRevocationService.revoke(claims);
        sessionService.revokeAll(claims.userId());
    }

    private JwtClaims requireClaims(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof JwtClaims claims)) {
            throw new UnauthorizedException("Authentication required");
        }
        return claims;
    }
}
