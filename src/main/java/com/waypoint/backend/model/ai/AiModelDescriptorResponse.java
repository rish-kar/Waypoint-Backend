package com.waypoint.backend.model.ai;

public record AiModelDescriptorResponse(
        String id,
        String displayName,
        boolean enabled,
        String mode
) {
}
