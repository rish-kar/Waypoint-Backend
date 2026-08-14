package com.waypoint.backend.model.admin;

import java.util.List;

public record PremiumSpecialSummaryResponse(
        long count,
        List<PremiumSpecialGrantResponse> users
) {
}
