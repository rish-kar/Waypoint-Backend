package com.waypoint.backend.model.ai;

public record ByokStatusResponse(
        boolean eligible,
        boolean configured,
        boolean active,
        String selectedModel
) {
}
