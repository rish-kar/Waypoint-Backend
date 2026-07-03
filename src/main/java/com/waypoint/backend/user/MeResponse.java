package com.waypoint.backend.user;

import com.waypoint.backend.entitlement.EntitlementResponse;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl,
        EntitlementResponse entitlement
) {
}
