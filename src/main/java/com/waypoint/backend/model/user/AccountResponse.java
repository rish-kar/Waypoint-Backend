package com.waypoint.backend.model.user;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.plan.PlanResponse;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl,
        String phoneNumber,
        String phoneCountryCode,
        String locale,
        PlanResponse plan,
        EntitlementResponse entitlement
) {
}
