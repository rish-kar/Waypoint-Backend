package com.waypoint.backend.model.admin;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> items,
        long totalElements,
        int page,
        int size,
        int totalPages,
        String sort,
        String direction
) {
}
