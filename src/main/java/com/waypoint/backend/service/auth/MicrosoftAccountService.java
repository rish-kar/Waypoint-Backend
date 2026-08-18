package com.waypoint.backend.service.auth;

import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftProviderCredentialEntity;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.MicrosoftProviderCredentialRepository;
import com.waypoint.backend.security.oauth.MicrosoftTokenCipher;
import com.waypoint.backend.service.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MicrosoftAccountService {
    private final MicrosoftProviderCredentialRepository credentialRepository;
    private final MicrosoftTokenCipher tokenCipher;
    private final UserService userService;

    public MicrosoftAccountService(MicrosoftProviderCredentialRepository credentialRepository,
                                   MicrosoftTokenCipher tokenCipher,
                                   UserService userService) {
        this.credentialRepository = credentialRepository;
        this.tokenCipher = tokenCipher;
        this.userService = userService;
    }

    @Transactional
    public UserEntity link(MicrosoftProfile profile, MicrosoftTokenSet tokens) {
        MicrosoftProviderCredentialEntity credential = credentialRepository.findByProviderUserId(profile.providerUserId()).orElse(null);
        UserEntity user = credential == null
                ? userService.findOrCreateMicrosoftUser(profile)
                : userService.updateMicrosoftUser(credential.getUser(), profile);
        if (credential == null) credential = new MicrosoftProviderCredentialEntity();
        credential.setUser(user);
        credential.setProviderUserId(profile.providerUserId());
        credential.setRefreshTokenCiphertext(tokenCipher.encrypt(tokens.refreshToken()));
        credential.setScopes(tokens.scopes());
        credential.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
        credential.setRevokedAt(null);
        credentialRepository.save(credential);
        return user;
    }
}
