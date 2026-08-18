package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAccountResponse(
        UUID id,
        String username,
        AdminRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminAccountResponse from(AdminAccountEntity account) {
        return new AdminAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}