package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingCheckoutSessionEntity;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.billing.BillingCheckoutSessionRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private static final Duration CHECKOUT_REUSE_WINDOW = Duration.ofMinutes(15);

    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties properties;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;
    private final BillingCheckoutSessionRepository checkoutSessionRepository;
    private final UserRepository userRepository;

    public BillingService(
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties properties,
            SubscriptionService subscriptionService,
            PlanRepository planRepository,
            BillingCheckoutSessionRepository checkoutSessionRepository,
            UserRepository userRepository
    ) {
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.properties = properties;
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.userRepository = userRepository;
    }

    public List<PlanResponse> availablePlans() {
        return planRepository
                .findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval.NONE)
                .stream()
                .map(PlanResponse::from)
                .toList();
    }

    @Transactional
    public String createCheckout(UserEntity user, CheckoutPlan plan) {
        if (plan == null) {
            throw new InvalidRequestException("plan is required");
        }
        if (user == null || user.getId() == null) {
            throw new InvalidRequestException("User account is unavailable");
        }

        UserEntity lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new InvalidRequestException("User account is unavailable"));
        if (subscriptionService.hasCheckoutBlockingSubscription(lockedUser.getId())) {
            throw new InvalidRequestException("A paid subscription is already active for this account");
        }

        Instant now = Instant.now();
        BillingCheckoutSessionEntity existingSession = checkoutSessionRepository.findById(lockedUser.getId())
                .orElse(null);
        if (isReusable(existingSession, now)) {
            if (existingSession.getPlan() == plan) {
                return existingSession.getCheckoutUrl();
            }
            throw new InvalidRequestException("A checkout for another billing plan is already pending for this account");
        }

        String variantId = switch (plan) {
            case MONTHLY -> properties.monthlyVariantId();
            case ANNUAL -> properties.annualVariantId();
        };
        if (!StringUtils.hasText(variantId)) {
            throw new InvalidRequestException("Requested billing plan is not configured");
        }

        String checkoutUrl = lemonSqueezyClient.createCheckout(lockedUser, plan, variantId);
        BillingCheckoutSessionEntity session = existingSession == null
                ? new BillingCheckoutSessionEntity()
                : existingSession;
        session.setUserId(lockedUser.getId());
        session.setPlan(plan);
        session.setCheckoutUrl(checkoutUrl);
        session.setExpiresAt(now.plus(CHECKOUT_REUSE_WINDOW));
        checkoutSessionRepository.save(session);
        return checkoutUrl;
    }

    public BillingStatusResponse billingStatus(UUID userId) {
        SubscriptionSnapshot subscription = subscriptionService.currentBilling(userId);
        return new BillingStatusResponse(
                subscription.premium() ? "PREMIUM" : "FREE",
                subscription.planCode(),
                subscription.status().name(),
                subscription.externalSubscriptionId(),
                subscription.trialEndsAt(),
                subscription.renewsAt(),
                subscription.endsAt()
        );
    }

    private boolean isReusable(BillingCheckoutSessionEntity session, Instant now) {
        return session != null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now)
                && StringUtils.hasText(session.getCheckoutUrl());
    }
}
