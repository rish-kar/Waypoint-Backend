package com.waypoint.backend.security.jwt;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, String email, UUID tokenId, Instant expiresAt) {
}
