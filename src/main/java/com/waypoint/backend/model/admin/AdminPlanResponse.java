package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;

import java.time.Instant;

public record AdminPlanResponse(
        PlanCode code,
        String displayName,
        BillingInterval billingInterval,
        int priceCents,
        String currency,
        boolean premium,
        boolean active,
        String providerVariantId,
        Instant createdAt,
        Instant updatedAt
) {
}
