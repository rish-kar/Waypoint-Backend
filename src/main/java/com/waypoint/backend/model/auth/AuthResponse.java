package com.waypoint.backend.model.auth;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserResponse user,
        EntitlementResponse entitlement
) {
}
