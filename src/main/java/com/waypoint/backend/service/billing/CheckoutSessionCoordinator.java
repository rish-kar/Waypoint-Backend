package com.waypoint.backend.service.billing;

import com.waypoint.backend.model.billing.BillingCheckoutSessionEntity;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.billing.BillingCheckoutSessionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutSessionCoordinator {
    private static final Duration PROVIDER_CALL_LEASE = Duration.ofSeconds(30);
    private static final Duration INTENT_RECOVERY_WINDOW = Duration.ofHours(1);
    private static final Duration COMPLETED_CHECKOUT_REUSE_WINDOW = Duration.ofMinutes(15);

    private final SubscriptionService subscriptionService;
    private final BillingCheckoutSessionRepository checkoutSessionRepository;
    private final UserRepository userRepository;

    public CheckoutSessionCoordinator(
            SubscriptionService subscriptionService,
            BillingCheckoutSessionRepository checkoutSessionRepository,
            UserRepository userRepository
    ) {
        this.subscriptionService = subscriptionService;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Reservation reserve(UUID userId, CheckoutPlan plan) {
        UserEntity lockedUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRequestException("User account is unavailable"));
        if (subscriptionService.hasCheckoutBlockingSubscription(userId)) {
            throw new InvalidRequestException("A paid subscription is already active for this account");
        }

        Instant now = Instant.now();
        BillingCheckoutSessionEntity session = checkoutSessionRepository.findById(userId).orElse(null);
        boolean recoveryOnly = false;
        if (session != null && session.getExpiresAt() != null && session.getExpiresAt().isAfter(now)) {
            if (session.getPlan() != plan) {
                throw new InvalidRequestException("A checkout for another billing plan is already pending for this account");
            }
            if (StringUtils.hasText(session.getCheckoutUrl())) {
                return new Reservation(lockedUser, session.getIntentId(), session.getCheckoutUrl(), false, false);
            }
            recoveryOnly = session.getIntentId() != null && session.getProviderRequestStartedAt() != null;
            if (recoveryOnly && session.getProviderRequestStartedAt().plus(PROVIDER_CALL_LEASE).isAfter(now)) {
                return new Reservation(lockedUser, session.getIntentId(), null, false, true);
            }
        }

        if (session == null) {
            session = new BillingCheckoutSessionEntity();
            session.setUserId(userId);
        }
        session.setPlan(plan);
        session.setCheckoutUrl(null);
        if (session.getIntentId() == null || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            session.setIntentId(UUID.randomUUID());
            recoveryOnly = false;
        }
        session.setProviderRequestStartedAt(now);
        session.setExpiresAt(now.plus(INTENT_RECOVERY_WINDOW));
        checkoutSessionRepository.saveAndFlush(session);
        return new Reservation(lockedUser, session.getIntentId(), null, true, recoveryOnly);
    }

    @Transactional
    public void complete(UUID userId, UUID intentId, String checkoutUrl) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRequestException("User account is unavailable"));
        BillingCheckoutSessionEntity session = checkoutSessionRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("Checkout reservation is unavailable"));
        if (intentId == null || !intentId.equals(session.getIntentId())) {
            throw new InvalidRequestException("Checkout reservation changed before it could be completed");
        }
        session.setCheckoutUrl(checkoutUrl);
        session.setProviderRequestStartedAt(null);
        session.setExpiresAt(Instant.now().plus(COMPLETED_CHECKOUT_REUSE_WINDOW));
        checkoutSessionRepository.save(session);
    }

    public record Reservation(
            UserEntity user,
            UUID intentId,
            String checkoutUrl,
            boolean providerOwner,
            boolean recoveryOnly
    ) {
    }
}
