package com.waypoint.backend.repository.user;

import com.waypoint.backend.model.user.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserEntity> findByEmail(String email);
}
