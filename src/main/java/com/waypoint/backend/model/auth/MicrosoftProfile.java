package com.waypoint.backend.model.auth;

public record MicrosoftProfile(
        String providerUserId,
        String email,
        String displayName,
        String locale
) {
    public MicrosoftProfile(String providerUserId, String email, String displayName) {
        this(providerUserId, email, displayName, null);
    }
}
