package com.waypoint.backend.repository.user;

import com.waypoint.backend.model.user.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserEntity> findByEmail(String email);
}
