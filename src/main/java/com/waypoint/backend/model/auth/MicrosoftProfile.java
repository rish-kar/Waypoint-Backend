package com.waypoint.backend.model.auth;

public record MicrosoftProfile(
        String providerUserId,
        String email,
        String displayName
) {
}
