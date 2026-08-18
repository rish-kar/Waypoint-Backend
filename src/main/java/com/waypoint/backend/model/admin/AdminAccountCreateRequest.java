package com.waypoint.backend.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 16, max = 200) String password,
        @NotNull AdminRole role,
        @NotBlank @Size(max = 200) String totpSecret
) {
}