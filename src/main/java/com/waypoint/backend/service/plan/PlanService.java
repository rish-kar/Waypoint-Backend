package com.waypoint.backend.service.plan;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionAccessPolicy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;

@Service
public class PlanService {
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final UserRepository userRepository;

    public PlanService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            UserRepository userRepository
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PlanEntity require(PlanCode code) {
        return planRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("Required plan is missing: " + code));
    }

    @Transactional
    public PlanEntity synchronizeUserPlan(UserEntity user) {
        Instant now = Instant.now();
        PlanCode targetCode = subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(subscription -> subscriptionAccessPolicy.evaluate(subscription, now).premium())
                .max(Comparator
                        .comparing((SubscriptionEntity subscription) -> validUntil(subscription, now))
                        .thenComparing(subscription -> subscription.getUpdatedAt() == null
                                ? Instant.EPOCH
                                : subscription.getUpdatedAt()))
                .map(this::planCodeFor)
                .orElse(PlanCode.FREE);

        if (user.getPlan() == null || user.getPlan().getCode() != targetCode) {
            user.setPlan(require(targetCode));
            userRepository.save(user);
        }
        return user.getPlan();
    }

    private Instant validUntil(SubscriptionEntity subscription, Instant now) {
        Instant validUntil = subscriptionAccessPolicy.evaluate(subscription, now).validUntil();
        return validUntil == null ? Instant.MAX : validUntil;
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
}
