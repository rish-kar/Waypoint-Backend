package com.waypoint.backend.model.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ByokApiKeyRequest(
        @NotBlank @Size(max = 512) String apiKey
) {
}
