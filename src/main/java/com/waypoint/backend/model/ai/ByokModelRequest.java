package com.waypoint.backend.model.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ByokModelRequest(
        @NotBlank @Size(max = 200) String model
) {
}
