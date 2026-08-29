package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MicrosoftUserProvisioningService {
    private final UserRepository userRepository;

    public MicrosoftUserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity create(MicrosoftProfile profile, String normalizedEmail, PlanEntity freePlan) {
        UserEntity user = new UserEntity();
        user.setProvider(UserService.MICROSOFT_PROVIDER);
        user.setProviderUserId(profile.providerUserId());
        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPlan(freePlan);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public UserEntity updateLogin(UserEntity user, MicrosoftProfile profile, String normalizedEmail, PlanEntity freePlan) {
        if (user.getPlan() == null) user.setPlan(freePlan);
        if (UserService.MICROSOFT_PROVIDER.equals(user.getProvider())) {
            user.setEmail(normalizedEmail);
            user.setDisplayName(profile.displayName());
        }
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }
}
