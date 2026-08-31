package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionService {
    private static final Set<SubscriptionStatus> RENEWING_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.PAST_DUE
    );
    private static final Set<SubscriptionStatus> CHECKOUT_BLOCKING_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.ON_TRIAL,
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.UNPAID,
            SubscriptionStatus.UNKNOWN
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final UserRepository userRepository;
    private final String adminEmail;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            UserRepository userRepository,
            @Value("${subscription-access.admin-email:}") String adminEmail
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.userRepository = userRepository;
        this.adminEmail = normalizeEmail(adminEmail);
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId) {
        return current(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId, Instant now) {
        if (isAdmin(userId)) {
            return adminSnapshot(now);
        }
        SpecialPremiumGrantEntity specialGrant = specialPremiumGrantRepository.findByUserId(userId)
                .filter(grant -> isActiveSpecialGrant(grant, now))
                .orElse(null);
        if (specialGrant != null) {
            return specialGrantSnapshot(specialGrant, now);
        }
        return currentBilling(userId, now);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SubscriptionSnapshot> currentForUsers(Set<UUID> userIds, Instant now) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, SubscriptionSnapshot> result = new HashMap<>();
        Set<UUID> remainingUserIds = new HashSet<>(userIds);
        if (!adminEmail.isBlank()) {
            userRepository.findAllById(userIds).stream()
                    .filter(this::isAdminUser)
                    .forEach(user -> {
                        UUID userId = user.getId();
                        result.put(userId, adminSnapshot(now));
                        remainingUserIds.remove(userId);
                    });
        }

        if (remainingUserIds.isEmpty()) {
            return result;
        }

        specialPremiumGrantRepository.findActiveForUsers(remainingUserIds, now).forEach(grant ->
                result.put(grant.getUser().getId(), specialGrantSnapshot(grant, now)));

        Set<UUID> billingUserIds = remainingUserIds.stream()
                .filter(userId -> !result.containsKey(userId))
                .collect(java.util.stream.Collectors.toSet());
        if (billingUserIds.isEmpty()) {
            return result;
        }

        Map<UUID, Candidate> bestPremium = new HashMap<>();
        subscriptionRepository.findCurrentPremiumCandidatesForUsers(
                billingUserIds,
                now,
                SubscriptionStatus.ON_TRIAL,
                RENEWING_STATUSES,
                SubscriptionStatus.CANCELLED
        ).forEach(subscription -> {
            SubscriptionAccessDecision decision = subscriptionAccessPolicy.evaluate(subscription, now);
            if (!decision.premium()) {
                return;
            }
            UUID userId = subscription.getUser().getId();
            Candidate candidate = new Candidate(subscription, decision);
            bestPremium.merge(userId, candidate, this::betterCandidate);
        });
        bestPremium.forEach((userId, candidate) -> result.put(userId, premiumSnapshot(
                candidate.subscription(),
                candidate.decision(),
                now
        )));

        Set<UUID> freeUserIds = billingUserIds.stream()
                .filter(userId -> !result.containsKey(userId))
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, SubscriptionEntity> latestByUser = new HashMap<>();
        if (!freeUserIds.isEmpty()) {
            subscriptionRepository.findLatestForUsers(freeUserIds).forEach(subscription ->
                    latestByUser.merge(
                            subscription.getUser().getId(),
                            subscription,
                            (left, right) -> comparableUpdatedAt(left).isAfter(comparableUpdatedAt(right)) ? left : right
                    ));
        }
        freeUserIds.forEach(userId -> {
            SubscriptionEntity latest = latestByUser.get(userId);
            SubscriptionStatus status = latest == null || latest.getStatus() == null
                    ? SubscriptionStatus.INACTIVE
                    : latest.getStatus();
            result.put(userId, freeSnapshot(status, latest, now));
        });
        return result;
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot currentBilling(UUID userId) {
        return currentBilling(userId, Instant.now());
    }

    SubscriptionSnapshot currentBilling(UUID userId, Instant now) {
        List<SubscriptionEntity> premiumCandidates = subscriptionRepository.findCurrentPremiumCandidates(
                userId,
                now,
                SubscriptionStatus.ON_TRIAL,
                RENEWING_STATUSES,
                SubscriptionStatus.CANCELLED,
                PageRequest.of(0, 1)
        );
        if (!premiumCandidates.isEmpty()) {
            SubscriptionEntity subscription = premiumCandidates.getFirst();
            SubscriptionAccessDecision decision = subscriptionAccessPolicy.evaluate(subscription, now);
            if (decision.premium()) {
                return premiumSnapshot(subscription, decision, now);
            }
        }

        SubscriptionEntity latest = subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (latest == null) {
            return freeSnapshot(SubscriptionStatus.INACTIVE, null, now);
        }
        SubscriptionStatus status = latest.getStatus() == null
                ? SubscriptionStatus.INACTIVE
                : latest.getStatus();
        return freeSnapshot(status, latest, now);
    }

    @Transactional(readOnly = true)
    public boolean hasCheckoutBlockingSubscription(UUID userId) {
        return hasCheckoutBlockingSubscription(userId, Instant.now());
    }

    boolean hasCheckoutBlockingSubscription(UUID userId, Instant now) {
        if (isAdmin(userId)) {
            return true;
        }
        return subscriptionRepository.existsCheckoutBlockingSubscription(
                userId,
                now,
                CHECKOUT_BLOCKING_STATUSES,
                SubscriptionStatus.CANCELLED
        );
    }

    private Candidate betterCandidate(Candidate left, Candidate right) {
        Comparator<Candidate> comparator = Comparator
                .comparing((Candidate candidate) -> comparableValidUntil(candidate.decision()))
                .thenComparing(candidate -> comparableUpdatedAt(candidate.subscription()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private SubscriptionSnapshot adminSnapshot(Instant checkedAt) {
        return new SubscriptionSnapshot(
                PlanCode.ADMIN,
                SubscriptionStatus.ADMIN,
                true,
                null,
                null,
                null,
                null,
                null,
                checkedAt
        );
    }

    private SubscriptionSnapshot specialGrantSnapshot(SpecialPremiumGrantEntity grant, Instant checkedAt) {
        return new SubscriptionSnapshot(
                PlanCode.PREMIUM_SPECIAL,
                SubscriptionStatus.PREMIUM_SPECIAL,
                true,
                null,
                null,
                null,
                null,
                grant.getValidUntil(),
                checkedAt
        );
    }

    private boolean isActiveSpecialGrant(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private boolean isAdmin(UUID userId) {
        if (adminEmail.isBlank() || userId == null) {
            return false;
        }
        return userRepository.findById(userId).map(this::isAdminUser).orElse(false);
    }

    private boolean isAdminUser(UserEntity user) {
        return user != null && adminEmail.equals(normalizeEmail(user.getEmail()));
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private SubscriptionSnapshot premiumSnapshot(
            SubscriptionEntity subscription,
            SubscriptionAccessDecision decision,
            Instant checkedAt
    ) {
        return new SubscriptionSnapshot(
                planCodeFor(subscription),
                decision.status(),
                true,
                subscription.getExternalSubscriptionId(),
                subscription.getTrialEndsAt(),
                subscription.getRenewsAt(),
                subscription.getEndsAt(),
                decision.validUntil(),
                checkedAt
        );
    }

    private SubscriptionSnapshot freeSnapshot(
            SubscriptionStatus status,
            SubscriptionEntity subscription,
            Instant checkedAt
    ) {
        return new SubscriptionSnapshot(
                PlanCode.FREE,
                status,
                false,
                subscription == null ? null : subscription.getExternalSubscriptionId(),
                subscription == null ? null : subscription.getTrialEndsAt(),
                subscription == null ? null : subscription.getRenewsAt(),
                subscription == null ? null : subscription.getEndsAt(),
                null,
                checkedAt
        );
    }

    private PlanCode planCodeFor(SubscriptionEntity subscription) {
        if (CheckoutPlan.ANNUAL.name().equals(subscription.getPlan())) {
            return PlanCode.PREMIUM_ANNUAL;
        }
        if (CheckoutPlan.MONTHLY.name().equals(subscription.getPlan())) {
            return PlanCode.PREMIUM_MONTHLY;
        }
        return PlanCode.FREE;
    }

    private Instant comparableValidUntil(SubscriptionAccessDecision decision) {
        return decision.validUntil() == null ? Instant.MAX : decision.validUntil();
    }

    private Instant comparableUpdatedAt(SubscriptionEntity subscription) {
        return subscription.getUpdatedAt() == null ? Instant.EPOCH : subscription.getUpdatedAt();
    }

    private record Candidate(SubscriptionEntity subscription, SubscriptionAccessDecision decision) {
    }
}
