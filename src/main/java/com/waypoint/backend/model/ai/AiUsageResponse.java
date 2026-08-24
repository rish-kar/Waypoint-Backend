package com.waypoint.backend.model.ai;

public record AiUsageResponse(
        boolean cloudAiAllowed,
        boolean trialLimited,
        int trialLimit,
        int trialUsed,
        int trialRemaining,
        String subscriptionStatus
) {
}
