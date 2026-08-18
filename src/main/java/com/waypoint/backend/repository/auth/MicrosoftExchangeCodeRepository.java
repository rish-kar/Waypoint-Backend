package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.MicrosoftExchangeCodeEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface MicrosoftExchangeCodeRepository extends JpaRepository<MicrosoftExchangeCodeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MicrosoftExchangeCodeEntity> findByCodeHash(String codeHash);
}
