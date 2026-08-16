package com.waypoint.backend.security.jwt;

import com.waypoint.backend.model.auth.RevokedJwtTokenEntity;
import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtRevocationService {
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(15);
    private static final int MAX_CACHE_ENTRIES = 50_000;

    private final RevokedJwtTokenRepository revokedJwtTokenRepository;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public JwtRevocationService(RevokedJwtTokenRepository revokedJwtTokenRepository) {
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(UUID tokenId) {
        if (tokenId == null) {
            return false;
        }

        Instant now = Instant.now();
        CacheEntry cached = cache.get(tokenId);
        if (cached != null && cached.validUntil().isAfter(now)) {
            return cached.revoked();
        }

        RevokedJwtTokenEntity revoked = revokedJwtTokenRepository.findById(tokenId).orElse(null);
        if (revoked != null && revoked.getExpiresAt() != null && revoked.getExpiresAt().isAfter(now)) {
            putCache(tokenId, new CacheEntry(true, revoked.getExpiresAt()), now);
            return true;
        }

        putCache(tokenId, new CacheEntry(false, now.plus(NEGATIVE_CACHE_TTL)), now);
        return false;
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

        RevokedJwtTokenEntity revokedToken = new RevokedJwtTokenEntity();
        revokedToken.setTokenId(claims.tokenId());
        revokedToken.setUserId(claims.userId());
        revokedToken.setExpiresAt(claims.expiresAt());
        revokedToken.setRevokedAt(now);
        revokedJwtTokenRepository.save(revokedToken);
        cache.put(claims.tokenId(), new CacheEntry(true, claims.expiresAt()));
    }

    @Scheduled(fixedDelayString = "${jwt.revocation-cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredRevocations() {
        Instant now = Instant.now();
        revokedJwtTokenRepository.deleteByExpiresAtBefore(now);
        cache.entrySet().removeIf(entry -> !entry.getValue().validUntil().isAfter(now));
    }

    private void putCache(UUID tokenId, CacheEntry entry, Instant now) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(candidate -> !candidate.getValue().validUntil().isAfter(now));
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            for (Map.Entry<UUID, CacheEntry> candidate : cache.entrySet()) {
                if (!candidate.getValue().revoked()) {
                    cache.remove(candidate.getKey(), candidate.getValue());
                    if (cache.size() < MAX_CACHE_ENTRIES) {
                        break;
                    }
                }
            }
        }
        if (cache.size() < MAX_CACHE_ENTRIES || entry.revoked()) {
            cache.put(tokenId, entry);
        }
    }

    private record CacheEntry(boolean revoked, Instant validUntil) {
    }
}
