package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.model.auth.MicrosoftOAuthTransactionEntity;
import com.waypoint.backend.repository.auth.MicrosoftOAuthTransactionRepository;
import com.waypoint.backend.security.oauth.MicrosoftTokenCipher;
import com.waypoint.backend.security.oauth.OAuthTokenGenerator;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MicrosoftOAuthStateService {
    private final MicrosoftOAuthTransactionRepository repository;
    private final MicrosoftOAuthProperties properties;
    private final MicrosoftTokenCipher tokenCipher;
    private final OAuthTokenGenerator tokenGenerator;

    public MicrosoftOAuthStateService(MicrosoftOAuthTransactionRepository repository,
                                      MicrosoftOAuthProperties properties,
                                      MicrosoftTokenCipher tokenCipher,
                                      OAuthTokenGenerator tokenGenerator) {
        this.repository = repository;
        this.properties = properties;
        this.tokenCipher = tokenCipher;
        this.tokenGenerator = tokenGenerator;
    }

    @Transactional
    public PendingAuthorization create(String extensionRedirectUri) {
        return create(extensionRedirectUri, null);
    }

    @Transactional
    public PendingAuthorization create(String extensionRedirectUri, UUID linkUserId) {
        String state = tokenGenerator.randomToken(32);
        String verifier = tokenGenerator.randomToken(64);
        Instant expiresAt = Instant.now().plusSeconds(properties.transactionTtlSeconds());
        MicrosoftOAuthTransactionEntity entity = new MicrosoftOAuthTransactionEntity();
        entity.setStateHash(tokenGenerator.sha256(state));
        entity.setCodeVerifierCiphertext(tokenCipher.encrypt(verifier));
        entity.setExtensionRedirectUri(extensionRedirectUri);
        entity.setLinkUserId(linkUserId);
        entity.setExpiresAt(expiresAt);
        repository.save(entity);
        return new PendingAuthorization(entity.getId(), state, tokenGenerator.pkceChallenge(verifier), extensionRedirectUri, expiresAt);
    }

    @Transactional
    public ConsumedAuthorization consume(String state) {
        if (state == null || state.isBlank()) throw invalidState();
        MicrosoftOAuthTransactionEntity entity = repository.findByStateHash(tokenGenerator.sha256(state)).orElseThrow(this::invalidState);
        Instant now = Instant.now();
        if (entity.getConsumedAt() != null || !entity.getExpiresAt().isAfter(now)) throw invalidState();
        entity.setConsumedAt(now);
        repository.save(entity);
        return new ConsumedAuthorization(
                entity.getId(),
                tokenCipher.decrypt(entity.getCodeVerifierCiphertext()),
                entity.getExtensionRedirectUri(),
                entity.getLinkUserId()
        );
    }

    private InvalidRequestException invalidState() {
        return new InvalidRequestException("Invalid, expired, or replayed Microsoft OAuth state");
    }

    public record PendingAuthorization(UUID transactionId, String state, String codeChallenge,
                                       String extensionRedirectUri, Instant expiresAt) { }

    public record ConsumedAuthorization(UUID transactionId, String codeVerifier, String extensionRedirectUri,
                                        UUID linkUserId) { }
}
