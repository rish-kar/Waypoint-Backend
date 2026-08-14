package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.subscription.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionResponse(
        UUID id,
        UUID userId,
        String email,
        String provider,
        String externalCustomerId,
        String externalSubscriptionId,
        String externalProductId,
        String externalVariantId,
        String plan,
        SubscriptionStatus status,
        Instant renewsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
}
