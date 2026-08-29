package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
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

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    public static final String GOOGLE_PROVIDER = "GOOGLE";

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

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public UserEntity updatePhoneNumber(UUID userId, String phoneNumber, String phoneCountryCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String normalizedPhone = phoneNumber == null ? null : phoneNumber.trim();
        String normalizedCountry = phoneCountryCode == null ? null : phoneCountryCode.trim().toUpperCase(Locale.ROOT);
        boolean hasPhone = normalizedPhone != null && !normalizedPhone.isBlank();
        user.setPhoneNumber(hasPhone ? normalizedPhone : null);
        user.setPhoneCountryCode(hasPhone && normalizedCountry != null && !normalizedCountry.isBlank() ? normalizedCountry : null);
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
