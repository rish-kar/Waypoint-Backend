package com.waypoint.backend.user;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String pictureUrl
) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getPictureUrl());
    }
}
