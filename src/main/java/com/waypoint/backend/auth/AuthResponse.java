package com.waypoint.backend.auth;

import com.waypoint.backend.entitlement.EntitlementResponse;
import com.waypoint.backend.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        EntitlementResponse entitlement
) {
}
