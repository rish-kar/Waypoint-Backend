package com.waypoint.backend.model.auth;

public record GoogleOAuthStartResponse(
        String transactionId,
        String authorizationUrl,
        long expiresIn
) {
}
