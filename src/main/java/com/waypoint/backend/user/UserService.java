package com.waypoint.backend.user;

import com.waypoint.backend.auth.GoogleProfile;
import com.waypoint.backend.common.NotFoundException;
import com.waypoint.backend.plan.PlanCode;
import com.waypoint.backend.plan.PlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    public static final String GOOGLE_PROVIDER = "GOOGLE";

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
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setProvider(GOOGLE_PROVIDER);
                    created.setProviderUserId(profile.providerUserId());
                    created.setCreatedAt(Instant.now());
                    created.setPlan(planService.require(PlanCode.FREE));
                    return created;
                });

        if (user.getPlan() == null) {
            user.setPlan(planService.require(PlanCode.FREE));
        }
        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPictureUrl(profile.pictureUrl());
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
