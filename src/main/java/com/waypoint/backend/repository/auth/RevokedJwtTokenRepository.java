package com.waypoint.backend.repository.auth;

import com.waypoint.backend.model.auth.RevokedJwtTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface RevokedJwtTokenRepository extends JpaRepository<RevokedJwtTokenEntity, UUID> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
