package com.waypoint.backend.auth;

public record GoogleProfile(
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String pictureUrl,
        String audience,
        long expiresInSeconds
) {
    public GoogleProfile(
            String providerUserId,
            String email,
            boolean emailVerified,
            String displayName,
            String pictureUrl,
            String audience
    ) {
        this(providerUserId, email, emailVerified, displayName, pictureUrl, audience, Long.MAX_VALUE);
    }
}
