package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.exception.NotFoundException;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final GoogleUserProvisioningService googleUserProvisioningService;

    public UserService(
            UserRepository userRepository,
            PlanService planService,
            GoogleUserProvisioningService googleUserProvisioningService
    ) {
        this.userRepository = userRepository;
        this.planService = planService;
        this.googleUserProvisioningService = googleUserProvisioningService;
    }

    public UserEntity findOrCreateGoogleUser(GoogleProfile profile) {
        String normalizedEmail = normalizeEmail(profile.email());
        PlanEntity freePlan = planService.require(PlanCode.FREE);
        UserEntity user = userRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                .orElse(null);

        if (user == null) {
            try {
                user = googleUserProvisioningService.create(profile, normalizedEmail, freePlan);
            } catch (DataIntegrityViolationException exception) {
                user = userRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                        .orElseThrow(() -> new UnauthorizedException("Google account identity conflict"));
            }
        }

        UserEntity saved = googleUserProvisioningService.updateLogin(user, profile, normalizedEmail, freePlan);
        planService.synchronizeUserPlan(saved);
        return saved;
    }

    @Transactional
    public UserEntity findOrCreateMicrosoftUser(MicrosoftProfile profile) {
        String normalizedEmail = normalizeEmail(profile.email());
        UserEntity user = userRepository.findByProviderAndProviderUserId(MICROSOFT_PROVIDER, profile.providerUserId())
                .orElseGet(() -> userRepository.findByEmail(normalizedEmail)
                        .orElseGet(() -> newMicrosoftUser(profile.providerUserId())));
        return updateMicrosoftUser(user, profile);
    }

    @Transactional
    public UserEntity updateMicrosoftUser(UserEntity user, MicrosoftProfile profile) {
        if (user.getPlan() == null) {
            user.setPlan(planService.require(PlanCode.FREE));
        }
        user.setEmail(normalizeEmail(profile.email()));
        user.setDisplayName(profile.displayName());
        user.setLastLoginAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        planService.synchronizeUserPlan(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserEntity newMicrosoftUser(String providerUserId) {
        UserEntity created = new UserEntity();
        created.setProvider(MICROSOFT_PROVIDER);
        created.setProviderUserId(providerUserId);
        created.setCreatedAt(Instant.now());
        created.setPlan(planService.require(PlanCode.FREE));
        return created;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
