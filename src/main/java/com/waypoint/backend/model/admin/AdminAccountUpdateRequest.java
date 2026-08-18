package com.waypoint.backend.model.admin;

import jakarta.validation.constraints.Size;

public record AdminAccountUpdateRequest(
        AdminRole role,
        Boolean active,
        @Size(min = 16, max = 200) String password,
        @Size(max = 200) String totpSecret
) {
}