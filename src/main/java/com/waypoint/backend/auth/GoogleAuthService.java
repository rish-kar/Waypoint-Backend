package com.waypoint.backend.auth;

import com.waypoint.backend.common.UnauthorizedException;
import com.waypoint.backend.entitlement.EntitlementService;
import com.waypoint.backend.security.JwtService;
import com.waypoint.backend.user.UserEntity;
import com.waypoint.backend.user.UserResponse;
import com.waypoint.backend.user.UserService;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthService {
    private final GoogleProfileClient googleProfileClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final EntitlementService entitlementService;
    private final GoogleProperties googleProperties;

    public GoogleAuthService(
            GoogleProfileClient googleProfileClient,
            UserService userService,
            JwtService jwtService,
            EntitlementService entitlementService,
            GoogleProperties googleProperties
    ) {
        this.googleProfileClient = googleProfileClient;
        this.userService = userService;
        this.jwtService = jwtService;
        this.entitlementService = entitlementService;
        this.googleProperties = googleProperties;
    }

    public AuthResponse login(String googleAccessToken) {
        GoogleProfile profile = googleProfileClient.fetchProfile(googleAccessToken);
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new UnauthorizedException("Google account is missing a provider user ID");
        }
        if (profile.email() == null || profile.email().isBlank() || !profile.emailVerified()) {
            throw new UnauthorizedException("Google account email is not verified");
        }
        if (profile.audience() == null || profile.audience().isBlank()) {
            throw new UnauthorizedException("Google access token audience is missing");
        }
        if (!googleProperties.clientId().equals(profile.audience())) {
            throw new UnauthorizedException("Google access token audience is invalid");
        }
        UserEntity user = userService.findOrCreateGoogleUser(profile);
        String waypointToken = jwtService.issueToken(user.getId(), user.getEmail());
        return new AuthResponse(
                waypointToken,
                "Bearer",
                jwtService.expirationSeconds(),
                UserResponse.from(user),
                entitlementService.currentEntitlement(user.getId(), false)
        );
    }
}
