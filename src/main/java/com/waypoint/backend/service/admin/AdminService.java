package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminUserResponse;
import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantResponse;
import com.waypoint.backend.model.admin.PremiumSpecialSummaryResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminService {
    private static final String PREMIUM_SPECIAL = "PREMIUM_SPECIAL";

    private final UserRepository userRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;

    public AdminService(
            UserRepository userRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            SubscriptionService subscriptionService,
            PlanService planService
    ) {
        this.userRepository = userRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.subscriptionService = subscriptionService;
        this.planService = planService;
    }

    @Transactional
    public PremiumSpecialGrantResponse grantPremiumSpecial(
            UUID userId,
            PremiumSpecialGrantRequest request,
            String adminId
    ) {
        Instant now = Instant.now();
        if (request.validUntil() != null && !request.validUntil().isAfter(now)) {
            throw new InvalidRequestException("validUntil must be in the future or omitted for lifetime access");
        }

        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = specialPremiumGrantRepository.findByUserId(userId)
                .orElseGet(SpecialPremiumGrantEntity::new);
        grant.setUser(user);
        grant.setActive(true);
        grant.setValidUntil(request.validUntil());
        grant.setReason(request.reason().trim());
        grant.setGrantedBy(adminId);
        grant.setGrantedAt(now);
        grant.setRevokedBy(null);
        grant.setRevokedAt(null);
        SpecialPremiumGrantEntity saved = specialPremiumGrantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        return toGrantResponse(saved, now);
    }

    @Transactional
    public PremiumSpecialGrantResponse revokePremiumSpecial(UUID userId, String adminId) {
        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = specialPremiumGrantRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Premium Special grant not found"));
        Instant now = Instant.now();
        grant.setActive(false);
        grant.setRevokedBy(adminId);
        grant.setRevokedAt(now);
        SpecialPremiumGrantEntity saved = specialPremiumGrantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        return toGrantResponse(saved, now);
    }

    @Transactional(readOnly = true)
    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        Instant now = Instant.now();
        List<PremiumSpecialGrantResponse> users = specialPremiumGrantRepository.findByActiveTrueOrderByGrantedAtDesc()
                .stream()
                .filter(grant -> isActive(grant, now))
                .map(grant -> toGrantResponse(grant, now))
                .toList();
        return new PremiumSpecialSummaryResponse(users.size(), users);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(UUID userId) {
        return toUserResponse(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse userByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toUserResponse(user);
    }

    private AdminUserResponse toUserResponse(UserEntity user) {
        SubscriptionSnapshot subscription = subscriptionService.current(user.getId());
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                subscription.planCode(),
                subscription.status(),
                subscription.premium(),
                subscription.validUntil()
        );
    }

    private PremiumSpecialGrantResponse toGrantResponse(SpecialPremiumGrantEntity grant, Instant now) {
        boolean active = isActive(grant, now);
        return new PremiumSpecialGrantResponse(
                grant.getUser().getId(),
                grant.getUser().getEmail(),
                PREMIUM_SPECIAL,
                active ? PREMIUM_SPECIAL : "REVOKED",
                active,
                grant.getValidUntil(),
                grant.getReason(),
                grant.getGrantedBy(),
                grant.getGrantedAt(),
                grant.getRevokedBy(),
                grant.getRevokedAt()
        );
    }

    private boolean isActive(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
