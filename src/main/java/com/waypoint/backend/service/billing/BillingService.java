package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.billing.ProviderPriceCatalog;
import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingService {
    private static final Duration PRICE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration PRICE_STALE_TTL = Duration.ofHours(1);

    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties properties;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;
    private final CheckoutSessionCoordinator checkoutSessionCoordinator;
    private final Object priceCacheLock = new Object();

    private volatile ProviderPriceCatalog cachedPriceCatalog;
    private volatile Instant cachedPriceCatalogAt;

    public BillingService(
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties properties,
            SubscriptionService subscriptionService,
            PlanRepository planRepository,
            CheckoutSessionCoordinator checkoutSessionCoordinator
    ) {
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.properties = properties;
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
        this.checkoutSessionCoordinator = checkoutSessionCoordinator;
    }

    public List<PlanResponse> availablePlans() {
        List<com.waypoint.backend.model.plan.PlanEntity> plans = planRepository
                .findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval.NONE);
        ProviderPriceCatalog catalog = priceCatalog();
        return plans.stream()
                .map(plan -> PlanResponse.from(plan, providerPrice(plan.getCode(), catalog), catalog.currency()))
                .sorted(Comparator.comparingInt(PlanResponse::priceCents))
                .toList();
    }

    public String createCheckout(UserEntity user, CheckoutPlan plan) {
        if (plan == null) {
            throw new InvalidRequestException("plan is required");
        }
        if (user == null || user.getId() == null) {
            throw new InvalidRequestException("User account is unavailable");
        }

        String variantId = switch (plan) {
            case MONTHLY -> properties.monthlyVariantId();
            case ANNUAL -> properties.annualVariantId();
        };
        if (!StringUtils.hasText(variantId)) {
            throw new InvalidRequestException("Requested billing plan is not configured");
        }

        CheckoutSessionCoordinator.Reservation reservation = checkoutSessionCoordinator.reserve(user.getId(), plan);
        if (StringUtils.hasText(reservation.checkoutUrl())) {
            return reservation.checkoutUrl();
        }
        if (!reservation.providerOwner()) {
            throw new InvalidRequestException("Checkout is already being prepared; retry shortly");
        }

        Optional<String> recoveredCheckout = lemonSqueezyClient.findCheckoutByIntent(variantId, reservation.intentId());
        if (recoveredCheckout.isPresent()) {
            checkoutSessionCoordinator.complete(user.getId(), reservation.intentId(), recoveredCheckout.get());
            return recoveredCheckout.get();
        }
        if (reservation.recoveryOnly()) {
            throw new InvalidRequestException("Checkout creation is being recovered; retry shortly");
        }

        String checkoutUrl = lemonSqueezyClient.createCheckout(
                reservation.user(),
                plan,
                variantId,
                reservation.intentId()
        );
        checkoutSessionCoordinator.complete(user.getId(), reservation.intentId(), checkoutUrl);
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

    private ProviderPriceCatalog priceCatalog() {
        Instant now = Instant.now();
        ProviderPriceCatalog cached = cachedPriceCatalog;
        Instant cachedAt = cachedPriceCatalogAt;
        if (cached != null && cachedAt != null && cachedAt.plus(PRICE_CACHE_TTL).isAfter(now)) {
            return cached;
        }

        synchronized (priceCacheLock) {
            now = Instant.now();
            cached = cachedPriceCatalog;
            cachedAt = cachedPriceCatalogAt;
            if (cached != null && cachedAt != null && cachedAt.plus(PRICE_CACHE_TTL).isAfter(now)) {
                return cached;
            }

            try {
                ProviderPriceCatalog fresh = lemonSqueezyClient.fetchPriceCatalog(
                        properties.monthlyVariantId(),
                        properties.annualVariantId()
                );
                cachedPriceCatalog = fresh;
                cachedPriceCatalogAt = now;
                return fresh;
            } catch (RuntimeException exception) {
                if (cached != null && cachedAt != null && cachedAt.plus(PRICE_STALE_TTL).isAfter(now)) {
                    return cached;
                }
                throw exception;
            }
        }
    }

    private int providerPrice(PlanCode code, ProviderPriceCatalog catalog) {
        return switch (code) {
            case PREMIUM_MONTHLY -> catalog.monthlyPriceCents();
            case PREMIUM_ANNUAL -> catalog.annualPriceCents();
            default -> throw new IllegalStateException("Unsupported billable plan: " + code);
        };
    }
}
