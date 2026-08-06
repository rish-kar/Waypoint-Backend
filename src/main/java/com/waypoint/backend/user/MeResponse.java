package com.waypoint.backend.user;

import com.waypoint.backend.entitlement.EntitlementResponse;
import com.waypoint.backend.plan.PlanResponse;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl,
        PlanResponse plan,
        EntitlementResponse entitlement
) {
}
