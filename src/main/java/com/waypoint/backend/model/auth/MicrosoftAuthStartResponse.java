package com.waypoint.backend.model.auth;

import java.util.UUID;

public record MicrosoftAuthStartResponse(
        String authorizationUrl,
        UUID transactionId,
        long expiresIn
) {
}
