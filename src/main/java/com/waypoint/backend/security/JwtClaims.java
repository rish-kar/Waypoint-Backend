package com.waypoint.backend.security;

import java.util.UUID;

public record JwtClaims(UUID userId, String email) {
}
