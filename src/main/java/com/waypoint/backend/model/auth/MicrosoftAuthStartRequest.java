package com.waypoint.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MicrosoftAuthStartRequest(
        @NotBlank @Size(max = 2048) String redirectUri
) {
}
