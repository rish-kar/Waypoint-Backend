package com.waypoint.backend.model.ai;

import java.util.List;

public record AiChatRequest(
        String question,
        String pageTitle,
        String pageDescription,
        String pageText,
        List<AiChatMessage> history,
        boolean allowGeneral,
        String model
) {
    public AiChatRequest {
        question = truncate(question, 500);
        pageTitle = truncate(pageTitle, 220);
        pageDescription = truncate(pageDescription, 1000);
        pageText = truncate(pageText, 14000);
        history = history == null
                ? List.of()
                : List.copyOf(history.subList(0, Math.min(history.size(), 12)));

        // Cloud AI testing currently has no frontend plan/auth enforcement.
        // The backend owns the provider/model choice, so ignore any client model value.
        model = null;
        allowGeneral = true;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
