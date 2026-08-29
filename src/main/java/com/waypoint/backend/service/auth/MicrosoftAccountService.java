package com.waypoint.backend.service.auth;

import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftProviderCredentialEntity;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.MicrosoftProviderCredentialRepository;
import com.waypoint.backend.service.user.UserService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MicrosoftAccountService {
    private final MicrosoftProviderCredentialRepository credentialRepository;
    private final MicrosoftCredentialService credentialService;
    private final UserService userService;

    public MicrosoftAccountService(MicrosoftProviderCredentialRepository credentialRepository,
                                   MicrosoftCredentialService credentialService,
                                   UserService userService) {
        this.credentialRepository = credentialRepository;
        this.credentialService = credentialService;
        this.userService = userService;
    }

    public UserEntity link(MicrosoftProfile profile, MicrosoftTokenSet tokens, UUID explicitLinkUserId) {
        MicrosoftProviderCredentialEntity existing = credentialRepository.findByProviderUserId(profile.providerUserId()).orElse(null);
        if (existing != null) {
            if (explicitLinkUserId != null && !existing.getUser().getId().equals(explicitLinkUserId)) {
                throw new UnauthorizedException("Microsoft account is linked to a different Waypoint account");
            }
            UserEntity user = userService.updateMicrosoftUser(existing.getUser(), profile);
            credentialService.updateExisting(profile.providerUserId(), user.getId(), tokens);
            return user;
        }

        UserEntity intendedUser = explicitLinkUserId == null
                ? userService.findOrCreateMicrosoftUser(profile)
                : userService.markMicrosoftLinkedLogin(explicitLinkUserId);

        UserEntity storedUser;
        try {
            storedUser = credentialService.create(intendedUser.getId(), profile.providerUserId(), tokens);
        } catch (DataIntegrityViolationException exception) {
            storedUser = credentialService.updateExisting(profile.providerUserId(), intendedUser.getId(), tokens);
        }
        return userService.updateMicrosoftUser(storedUser, profile);
    }
}
