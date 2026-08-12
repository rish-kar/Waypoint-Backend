package com.waypoint.backend.model.user;

import com.waypoint.backend.model.plan.PlanResponse;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl,
        PlanResponse plan
) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                PlanResponse.from(user.getPlan())
        );
    }
}
