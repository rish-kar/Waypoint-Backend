package com.waypoint.backend.model.ai;

public record AiChatResponse(
        String answer,
        String source,
        String modelId
) {
}
