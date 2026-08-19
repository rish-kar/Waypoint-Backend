package com.waypoint.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionRefreshRequest(
        @NotBlank
        @Size(max = 512)
        String refreshToken
) {
}
