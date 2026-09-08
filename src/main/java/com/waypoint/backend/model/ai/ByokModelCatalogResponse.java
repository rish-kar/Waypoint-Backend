package com.waypoint.backend.model.ai;

import java.util.List;

public record ByokModelCatalogResponse(
        String provider,
        List<String> models,
        String selectedModel
) {
}
