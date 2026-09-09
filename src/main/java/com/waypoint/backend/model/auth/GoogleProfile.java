package com.waypoint.backend.model.auth;

public record GoogleProfile(
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String pictureUrl,
        String locale,
        String audience,
        long expiresInSeconds
) {
    public GoogleProfile(
            String providerUserId,
            String email,
            boolean emailVerified,
            String displayName,
            String pictureUrl,
            String audience,
            long expiresInSeconds
    ) {
        this(providerUserId, email, emailVerified, displayName, pictureUrl, null, audience, expiresInSeconds);
    }

    public GoogleProfile(
            String providerUserId,
            String email,
            boolean emailVerified,
            String displayName,
            String pictureUrl,
            String audience
    ) {
        this(providerUserId, email, emailVerified, displayName, pictureUrl, null, audience, Long.MAX_VALUE);
    }
}
