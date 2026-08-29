package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.WaypointRefreshSessionEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WaypointRefreshSessionRepository extends JpaRepository<WaypointRefreshSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WaypointRefreshSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("update WaypointRefreshSessionEntity session set session.revokedAt = :now, session.updatedAt = :now " +
            "where session.userId = :userId and session.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);
    long deleteByRevokedAtBefore(Instant cutoff);
}
