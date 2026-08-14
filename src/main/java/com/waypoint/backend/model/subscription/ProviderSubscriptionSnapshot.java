package com.waypoint.backend.model.subscription;

import java.time.Instant;

public record ProviderSubscriptionSnapshot(
        String externalSubscriptionId,
        String userEmail,
        String externalCustomerId,
        String externalProductId,
        String externalVariantId,
        String status,
        Instant renewsAt,
        Instant endsAt,
        Instant providerUpdatedAt
) {
}
