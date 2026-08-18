package com.waypoint.backend.model.ai;

import java.util.List;

public record AiIntentResponse(
        String kind,
        String action,
        String scope,
        String target,
        List<String> matchTerms,
        List<String> sites,
        boolean explicitCurrent,
        boolean explicitAll,
        String groupTitle,
        String workspaceName,
        String wakeAt,
        String clarification,
        String modelId
) {
    public AiIntentResponse {
        matchTerms = matchTerms == null ? List.of() : List.copyOf(matchTerms);
        sites = sites == null ? List.of() : List.copyOf(sites);
    }
}
