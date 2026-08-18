package com.waypoint.backend.security.jwt;

import com.waypoint.backend.model.auth.RevokedJwtTokenEntity;
import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtRevocationService {
    private static final String REDIS_KEY_PREFIX = "waypoint:jwt:revoked:";

    private final RevokedJwtTokenRepository revokedJwtTokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final boolean distributedStateEnabled;

    public JwtRevocationService(
            RevokedJwtTokenRepository revokedJwtTokenRepository,
            StringRedisTemplate redisTemplate,
            @Value("${security.distributed-state-enabled:false}") boolean distributedStateEnabled
    ) {
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
        this.redisTemplate = redisTemplate;
        this.distributedStateEnabled = distributedStateEnabled;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(UUID tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (distributedStateEnabled) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(tokenId)));
            } catch (RuntimeException exception) {
                return isRevokedInDatabase(tokenId, Instant.now());
            }
        }
        return isRevokedInDatabase(tokenId, Instant.now());
    }

    @Transactional
    public void revoke(JwtClaims claims) {
        if (claims == null || claims.tokenId() == null || claims.userId() == null || claims.expiresAt() == null) {
            return;
        }

        Instant now = Instant.now();
        if (!claims.expiresAt().isAfter(now)) {
            return;
        }

        if (distributedStateEnabled) {
            Duration ttl = Duration.between(now, claims.expiresAt());
            redisTemplate.opsForValue().set(redisKey(claims.tokenId()), "1", ttl);
        }

        RevokedJwtTokenEntity revokedToken = new RevokedJwtTokenEntity();
        revokedToken.setTokenId(claims.tokenId());
        revokedToken.setUserId(claims.userId());
        revokedToken.setExpiresAt(claims.expiresAt());
        revokedToken.setRevokedAt(now);
        revokedJwtTokenRepository.save(revokedToken);
    }

    @Scheduled(fixedDelayString = "${jwt.revocation-cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredRevocations() {
        revokedJwtTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private boolean isRevokedInDatabase(UUID tokenId, Instant now) {
        RevokedJwtTokenEntity revoked = revokedJwtTokenRepository.findById(tokenId).orElse(null);
        return revoked != null && revoked.getExpiresAt() != null && revoked.getExpiresAt().isAfter(now);
    }

    private String redisKey(UUID tokenId) {
        return REDIS_KEY_PREFIX + tokenId;
    }
}
