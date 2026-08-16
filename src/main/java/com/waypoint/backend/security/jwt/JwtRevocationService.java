package com.waypoint.backend.security.jwt;

import com.waypoint.backend.model.auth.RevokedJwtTokenEntity;
import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtRevocationService {
    private final RevokedJwtTokenRepository revokedJwtTokenRepository;

    public JwtRevocationService(RevokedJwtTokenRepository revokedJwtTokenRepository) {
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(UUID tokenId) {
        return tokenId != null && revokedJwtTokenRepository.existsById(tokenId);
    }

    @Transactional
    public void revoke(JwtClaims claims) {
        if (claims == null || claims.tokenId() == null || claims.userId() == null || claims.expiresAt() == null) {
            return;
        }

        Instant now = Instant.now();
        revokedJwtTokenRepository.deleteByExpiresAtBefore(now);
        if (!claims.expiresAt().isAfter(now)) {
            return;
        }

        RevokedJwtTokenEntity revokedToken = new RevokedJwtTokenEntity();
        revokedToken.setTokenId(claims.tokenId());
        revokedToken.setUserId(claims.userId());
        revokedToken.setExpiresAt(claims.expiresAt());
        revokedToken.setRevokedAt(now);
        revokedJwtTokenRepository.save(revokedToken);
    }
}
