package com.waypoint.backend.model.auth;

public record MicrosoftTokenSet(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String scopes
) {
}
