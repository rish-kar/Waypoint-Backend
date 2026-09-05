package com.waypoint.backend.model.ai;

import java.util.List;

public record ByokStatusResponse(
        boolean eligible,
        boolean configured,
        boolean active,
        String provider,
        String selectedModel,
        List<ByokProviderResponse> providers
) {
}
