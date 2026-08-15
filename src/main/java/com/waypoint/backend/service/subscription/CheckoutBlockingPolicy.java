package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import java.time.Instant;

final class CheckoutBlockingPolicy {
    boolean blocks(SubscriptionEntity subscription, Instant now) {
        String externalSubscriptionId = subscription.getExternalSubscriptionId();
        if (externalSubscriptionId == null || externalSubscriptionId.isBlank()) {
            return false;
        }
        SubscriptionStatus status = subscription.getStatus() == null
                ? SubscriptionStatus.UNKNOWN
                : subscription.getStatus();
        return switch (status) {
            case ACTIVE, ON_TRIAL, UNKNOWN -> true;
            case CANCELLED -> subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(now);
            default -> false;
        };
    }
}
