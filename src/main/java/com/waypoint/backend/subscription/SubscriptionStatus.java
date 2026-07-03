package com.waypoint.backend.subscription;

import java.util.Locale;

public enum SubscriptionStatus {
    ACTIVE,
    ON_TRIAL,
    CANCELLED,
    EXPIRED,
    REFUNDED,
    INACTIVE,
    UNKNOWN;

    public static SubscriptionStatus fromExternal(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "on_trial", "trialing", "trial" -> ON_TRIAL;
            case "cancelled", "canceled" -> CANCELLED;
            case "expired" -> EXPIRED;
            case "refunded" -> REFUNDED;
            case "inactive" -> INACTIVE;
            default -> UNKNOWN;
        };
    }
}
