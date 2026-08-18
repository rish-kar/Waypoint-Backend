package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    public static final String GOOGLE_PROVIDER = "GOOGLE";
    public static final String MICROSOFT_PROVIDER = "MICROSOFT";

    private final UserRepository userRepository;
    private final PlanService planService;

    public UserService(UserRepository userRepository, PlanService planService) {
        this.userRepository = userRepository;
        this.planService = planService;
    }

    @Transactional
    public UserEntity findOrCreateGoogleUser(GoogleProfile profile) {
        String normalizedEmail = normalizeEmail(profile.email());
        UserEntity user = userRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                .orElseGet(() -> userRepository.findByEmail(normalizedEmail)
                        .orElseGet(() -> newUser(GOOGLE_PROVIDER, profile.providerUserId())));
        if (!GOOGLE_PROVIDER.equals(user.getProvider())) {
            user.setProvider(GOOGLE_PROVIDER);
            user.setProviderUserId(profile.providerUserId());
        }
        ensureFreePlan(user);
        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPictureUrl(profile.pictureUrl());
        user.setLastLoginAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        planService.synchronizeUserPlan(saved);
        return saved;
    }

    @Transactional
    public UserEntity findOrCreateMicrosoftUser(MicrosoftProfile profile) {
        String normalizedEmail = normalizeEmail(profile.email());
        UserEntity user = userRepository.findByProviderAndProviderUserId(MICROSOFT_PROVIDER, profile.providerUserId())
                .orElseGet(() -> userRepository.findByEmail(normalizedEmail)
                        .orElseGet(() -> newUser(MICROSOFT_PROVIDER, profile.providerUserId())));
        return updateMicrosoftUser(user, profile);
    }

    @Transactional
    public UserEntity updateMicrosoftUser(UserEntity user, MicrosoftProfile profile) {
        ensureFreePlan(user);
        user.setEmail(normalizeEmail(profile.email()));
        user.setDisplayName(profile.displayName());
        user.setLastLoginAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        planService.synchronizeUserPlan(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserEntity newUser(String provider, String providerUserId) {
        UserEntity created = new UserEntity();
        created.setProvider(provider);
        created.setProviderUserId(providerUserId);
        created.setCreatedAt(Instant.now());
        created.setPlan(planService.require(PlanCode.FREE));
        return created;
    }

    private void ensureFreePlan(UserEntity user) {
        if (user.getPlan() == null) user.setPlan(planService.require(PlanCode.FREE));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
