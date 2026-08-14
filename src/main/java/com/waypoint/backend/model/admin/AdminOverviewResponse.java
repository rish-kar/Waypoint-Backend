package com.waypoint.backend.model.admin;

public record AdminOverviewResponse(
        long users,
        long premiumUsers,
        long subscriptions,
        long activeSpecialGrants,
        long webhookEvents,
        long failedWebhookEvents
) {
}
