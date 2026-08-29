package com.waypoint.backend.service.auth;

import com.waypoint.backend.model.auth.MicrosoftProviderCredentialEntity;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.MicrosoftProviderCredentialRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.oauth.MicrosoftTokenCipher;
import com.waypoint.backend.utilities.client.microsoft.MicrosoftOAuthClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MicrosoftCredentialService {
    private final MicrosoftProviderCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final MicrosoftTokenCipher tokenCipher;
    private final MicrosoftOAuthClient microsoftOAuthClient;

    public MicrosoftCredentialService(MicrosoftProviderCredentialRepository credentialRepository,
                                      UserRepository userRepository,
                                      MicrosoftTokenCipher tokenCipher,
                                      MicrosoftOAuthClient microsoftOAuthClient) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.tokenCipher = tokenCipher;
        this.microsoftOAuthClient = microsoftOAuthClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity create(UUID userId, String providerUserId, MicrosoftTokenSet tokens) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Waypoint account is unavailable"));
        MicrosoftProviderCredentialEntity existing = credentialRepository.findByUserIdForUpdate(userId).orElse(null);
        if (existing != null) {
            if (!providerUserId.equals(existing.getProviderUserId())) {
                throw new UnauthorizedException("A different Microsoft account is already linked to this Waypoint account");
            }
            applyTokens(existing, tokens);
            credentialRepository.saveAndFlush(existing);
            return user;
        }
        MicrosoftProviderCredentialEntity credential = new MicrosoftProviderCredentialEntity();
        credential.setUser(user);
        credential.setProviderUserId(providerUserId);
        applyTokens(credential, tokens);
        credentialRepository.saveAndFlush(credential);
        return user;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity updateExisting(String providerUserId, UUID expectedUserId, MicrosoftTokenSet tokens) {
        MicrosoftProviderCredentialEntity credential = credentialRepository.findByProviderUserIdForUpdate(providerUserId)
                .orElseThrow(() -> new UnauthorizedException("Microsoft account identity conflict"));
        if (!credential.getUser().getId().equals(expectedUserId)) {
            throw new UnauthorizedException("Microsoft account is linked to a different Waypoint account");
        }
        applyTokens(credential, tokens);
        credentialRepository.saveAndFlush(credential);
        return credential.getUser();
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public MicrosoftTokenSet refreshAccessToken(UUID userId) {
        MicrosoftProviderCredentialEntity credential = credentialRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Microsoft account is not connected"));
        if (credential.getRevokedAt() != null) {
            throw new UnauthorizedException("Microsoft account is not connected");
        }
        String refreshToken = tokenCipher.decrypt(credential.getRefreshTokenCiphertext());
        try {
            MicrosoftTokenSet rotated = microsoftOAuthClient.refreshAccessToken(refreshToken);
            applyTokens(credential, rotated);
            credentialRepository.saveAndFlush(credential);
            return rotated;
        } catch (UnauthorizedException exception) {
            credentialRepository.delete(credential);
            credentialRepository.flush();
            throw exception;
        }
    }

    @Transactional
    public void disconnect(UUID userId) {
        credentialRepository.findByUserIdForUpdate(userId).ifPresent(credentialRepository::delete);
    }

    private void applyTokens(MicrosoftProviderCredentialEntity credential, MicrosoftTokenSet tokens) {
        credential.setRefreshTokenCiphertext(tokenCipher.encrypt(tokens.refreshToken()));
        credential.setScopes(tokens.scopes());
        credential.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
        credential.setRevokedAt(null);
    }
}
