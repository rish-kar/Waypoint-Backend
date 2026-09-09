package com.waypoint.backend.model.subscription;

import java.util.Locale;

public enum SubscriptionStatus {
    ACTIVE,
    ON_TRIAL,
    PAUSED,
    PAST_DUE,
    UNPAID,
    CANCELLED,
    EXPIRED,
    REFUNDED,
    PREMIUM_SPECIAL,
    ADMIN,
    INACTIVE,
    UNKNOWN;

    public static SubscriptionStatus fromExternal(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "on_trial", "trialing", "trial" -> ON_TRIAL;
            case "paused" -> PAUSED;
            case "past_due" -> PAST_DUE;
            case "unpaid" -> UNPAID;
            case "cancelled", "canceled" -> CANCELLED;
            case "expired" -> EXPIRED;
            case "refunded" -> REFUNDED;
            case "inactive" -> INACTIVE;
            default -> UNKNOWN;
        };
    }
}
