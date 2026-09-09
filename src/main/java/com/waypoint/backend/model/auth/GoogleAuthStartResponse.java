package com.waypoint.backend.model.auth;

public record GoogleAuthStartResponse(
        String authorizationUrl,
        String exchangeCode,
        long expiresIn
) {
}
