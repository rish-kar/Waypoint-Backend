package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GoogleUserProvisioningService {
    private final UserRepository userRepository;

    public GoogleUserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity create(GoogleProfile profile, String normalizedEmail, PlanEntity freePlan) {
        UserEntity user = new UserEntity();
        user.setProvider(UserService.GOOGLE_PROVIDER);
        user.setProviderUserId(profile.providerUserId());
        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPictureUrl(profile.pictureUrl());
        user.setPlan(freePlan);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public UserEntity updateLogin(UserEntity user, GoogleProfile profile, String normalizedEmail, PlanEntity freePlan) {
        if (user.getPlan() == null) {
            user.setPlan(freePlan);
        }
        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPictureUrl(profile.pictureUrl());
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }
}