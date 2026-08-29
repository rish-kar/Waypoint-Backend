package com.waypoint.backend.model.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiIntentRequest(
        @NotBlank @Size(max = 600) String request,
        boolean lastSelectionAvailable,
        @Size(max = 160) String lastSelectionTarget,
        @Size(max = 80) String currentTime,
        @Size(max = 64) String timeZone,
        @Size(max = 32) String locale,
        @Size(max = 40) String model
) {
}
