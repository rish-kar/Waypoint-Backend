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
        @Size(max = 40) String model
) {
    public AiChatRequest {
        question = truncate(question, 500);
        pageTitle = truncate(pageTitle, 220);
        pageDescription = truncate(pageDescription, 1000);
        pageText = truncate(pageText, 14000);
        history = history == null
                ? List.of()
                : List.copyOf(history.subList(0, Math.min(history.size(), 12)));

        // The backend owns the provider/model choice, so ignore any client model value.
        model = null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
