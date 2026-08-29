package com.waypoint.backend.model.auth;

public record GoogleOAuthStatusResponse(
        String status,
        String error
) {
}
