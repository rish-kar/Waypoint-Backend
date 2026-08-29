package com.waypoint.backend.controller.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleAuthRequest;
import com.waypoint.backend.model.auth.GoogleOAuthStartResponse;
import com.waypoint.backend.model.auth.GoogleOAuthStatusResponse;
import com.waypoint.backend.security.jwt.JwtClaims;
import com.waypoint.backend.security.jwt.JwtRevocationService;
import com.waypoint.backend.service.auth.GoogleAuthService;
import com.waypoint.backend.service.auth.GoogleOAuthWebService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final GoogleAuthService googleAuthService;
    private final GoogleOAuthWebService googleOAuthWebService;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(
            GoogleAuthService googleAuthService,
            GoogleOAuthWebService googleOAuthWebService,
            JwtRevocationService jwtRevocationService
    ) {
        this.googleAuthService = googleAuthService;
        this.googleOAuthWebService = googleOAuthWebService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.login(request.accessToken());
    }

    @PostMapping("/google/start")
    public GoogleOAuthStartResponse googleStart() {
        return googleOAuthWebService.start();
    }

    @GetMapping("/google/status")
    public GoogleOAuthStatusResponse googleStatus(@RequestParam String transactionId) {
        return googleOAuthWebService.status(transactionId);
    }

    @PostMapping("/google/exchange")
    public AuthResponse googleExchange(@RequestParam String transactionId) {
        return googleOAuthWebService.exchange(transactionId);
    }

    @GetMapping(value = "/google/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        GoogleOAuthWebService.CallbackPage page = googleOAuthWebService.callbackPage(code, state, error);
        String title = page.success() ? "Waypoint sign-in complete" : "Waypoint sign-in failed";
        String message = page.success()
                ? "Sign-in complete. You can close this tab and return to Waypoint."
                : "Sign-in failed. Return to Waypoint and try again.";
        String body = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title></head>"
                + "<body><main><h1>" + title + "</h1><p>" + message + "</p></main></body></html>";
        return ResponseEntity.status(page.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(body);
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
