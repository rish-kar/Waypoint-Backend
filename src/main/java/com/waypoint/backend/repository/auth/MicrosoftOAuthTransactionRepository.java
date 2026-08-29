package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.MicrosoftOAuthTransactionEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MicrosoftOAuthTransactionRepository extends JpaRepository<MicrosoftOAuthTransactionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MicrosoftOAuthTransactionEntity> findByStateHash(String stateHash);

    long deleteByExpiresAtBefore(Instant cutoff);
}
