package com.waypoint.backend.model.ai;

import java.util.List;

public record AiModelCatalogResponse(
        String defaultModel,
        List<AiModelDescriptorResponse> models
) {
    public AiModelCatalogResponse {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
