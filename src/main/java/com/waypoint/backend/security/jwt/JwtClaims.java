package com.waypoint.backend.security.jwt;

import java.util.UUID;

public record JwtClaims(UUID userId, String email) {
}
