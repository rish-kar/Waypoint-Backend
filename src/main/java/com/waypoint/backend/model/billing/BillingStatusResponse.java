package com.waypoint.backend.model.billing;

import com.waypoint.backend.model.plan.PlanCode;

import java.time.Instant;

public record BillingStatusResponse(
        String plan,
        PlanCode planCode,
        String status,
        String externalSubscriptionId,
        Instant renewsAt,
        Instant endsAt
) {
}
