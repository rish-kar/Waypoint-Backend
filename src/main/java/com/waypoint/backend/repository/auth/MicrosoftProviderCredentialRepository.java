package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.MicrosoftProviderCredentialEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MicrosoftProviderCredentialRepository extends JpaRepository<MicrosoftProviderCredentialEntity, UUID> {
    Optional<MicrosoftProviderCredentialEntity> findByProviderUserId(String providerUserId);
    Optional<MicrosoftProviderCredentialEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from MicrosoftProviderCredentialEntity credential where credential.providerUserId = :providerUserId")
    Optional<MicrosoftProviderCredentialEntity> findByProviderUserIdForUpdate(@Param("providerUserId") String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from MicrosoftProviderCredentialEntity credential where credential.user.id = :userId")
    Optional<MicrosoftProviderCredentialEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
