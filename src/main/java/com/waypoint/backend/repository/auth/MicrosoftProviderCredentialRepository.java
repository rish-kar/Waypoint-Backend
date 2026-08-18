package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.MicrosoftProviderCredentialEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MicrosoftProviderCredentialRepository extends JpaRepository<MicrosoftProviderCredentialEntity, UUID> {
    Optional<MicrosoftProviderCredentialEntity> findByProviderUserId(String providerUserId);
    Optional<MicrosoftProviderCredentialEntity> findByUserId(UUID userId);
}
