package com.waypoint.backend.model.auth;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.user.UserResponse;

public record SessionResponse(
        boolean authenticated,
        UserResponse user,
        EntitlementResponse entitlement
) {
    public static SessionResponse signedOut() {
        return new SessionResponse(false, null, null);
    }
}
