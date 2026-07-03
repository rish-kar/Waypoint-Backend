package com.waypoint.backend.auth;

public record GoogleProfile(
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String pictureUrl,
        String audience
) {
}
