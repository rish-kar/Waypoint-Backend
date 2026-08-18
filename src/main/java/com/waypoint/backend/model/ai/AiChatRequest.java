package com.waypoint.backend.model.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiChatRequest(
        @NotBlank @Size(max = 500) String question,
        @Size(max = 220) String pageTitle,
        @Size(max = 1000) String pageDescription,
        @NotBlank @Size(max = 14000) String pageText,
        @Valid @Size(max = 12) List<AiChatMessage> history,
        boolean allowGeneral,
        @Size(max = 40) String model
) {
}
