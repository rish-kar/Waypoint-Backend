package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl,
        String provider,
        String providerUserId,
        PlanCode persistedPlan,
        PlanCode plan,
        SubscriptionStatus status,
        boolean premium,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {
}
