package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        PlanCode plan,
        SubscriptionStatus status,
        boolean premium,
        Instant validUntil
) {
}
