package com.waypoint.backend.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PremiumSpecialGrantRequest(
        Instant validUntil,
        @NotBlank @Size(max = 255) String reason
) {
}
