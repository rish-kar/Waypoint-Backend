package com.waypoint.backend.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(@NotBlank String accessToken) {
}
