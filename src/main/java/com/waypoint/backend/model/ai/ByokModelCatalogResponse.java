package com.waypoint.backend.model.ai;

import java.util.List;

public record ByokModelCatalogResponse(
        List<String> models,
        String selectedModel
) {
}
