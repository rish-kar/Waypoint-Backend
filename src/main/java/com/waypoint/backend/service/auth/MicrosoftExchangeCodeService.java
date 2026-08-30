package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.model.auth.MicrosoftExchangeCodeEntity;
import com.waypoint.backend.repository.auth.MicrosoftExchangeCodeRepository;
import com.waypoint.backend.security.oauth.OAuthTokenGenerator;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MicrosoftExchangeCodeService {
    private final MicrosoftExchangeCodeRepository repository;
    private final MicrosoftOAuthProperties properties;
    private final OAuthTokenGenerator tokenGenerator;

    public MicrosoftExchangeCodeService(MicrosoftExchangeCodeRepository repository,
                                        MicrosoftOAuthProperties properties,
                                        OAuthTokenGenerator tokenGenerator) {
        this.repository = repository;
        this.properties = properties;
        this.tokenGenerator = tokenGenerator;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawCode = tokenGenerator.randomToken(32);
        MicrosoftExchangeCodeEntity entity = new MicrosoftExchangeCodeEntity();
        entity.setCodeHash(tokenGenerator.sha256(rawCode));
        entity.setUserId(userId);
        entity.setExpiresAt(Instant.now().plusSeconds(properties.exchangeCodeTtlSeconds()));
        repository.save(entity);
        return rawCode;
    }

    @Transactional
    public UUID consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) throw invalidCode();
        MicrosoftExchangeCodeEntity entity = repository.findByCodeHash(tokenGenerator.sha256(rawCode)).orElseThrow(this::invalidCode);
        Instant now = Instant.now();
        if (entity.getConsumedAt() != null || !entity.getExpiresAt().isAfter(now)) throw invalidCode();
        entity.setConsumedAt(now);
        repository.save(entity);
        return entity.getUserId();
    }

    private UnauthorizedException invalidCode() {
        return new UnauthorizedException("Invalid, expired, or replayed session exchange code");
    }
}
