package com.waypoint.backend.model.user;

import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
        @Size(max = 32) String phoneNumber
) {
}
