package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SubscriptionReconciliationService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final PlanService planService;

    public SubscriptionReconciliationService(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            PlanService planService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.planService = planService;
    }

    @Transactional
    public Result reconcile(ProviderSubscriptionSnapshot snapshot) {
        Optional<SubscriptionEntity> existing = subscriptionRepository
                .findByExternalSubscriptionIdForUpdate(snapshot.externalSubscriptionId());
        if (existing.isPresent()
                && existing.get().getLastProviderEventAt() != null
                && !snapshot.providerUpdatedAt().isAfter(existing.get().getLastProviderEventAt())) {
            return Result.STALE;
        }

        UserEntity user = existing.map(SubscriptionEntity::getUser)
                .orElseGet(() -> findUser(snapshot.userEmail()).orElse(null));
        if (user == null) {
            return Result.UNMATCHED_USER;
        }

        SubscriptionEntity subscription = existing.orElseGet(SubscriptionEntity::new);
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId(snapshot.externalSubscriptionId());
        setIfPresent(subscription::setExternalCustomerId, snapshot.externalCustomerId());
        setIfPresent(subscription::setExternalProductId, snapshot.externalProductId());
        if (StringUtils.hasText(snapshot.externalVariantId())) {
            subscription.setExternalVariantId(snapshot.externalVariantId());
            subscription.setPlan(subscriptionAccessPolicy.planForVariant(snapshot.externalVariantId()));
        } else if (!StringUtils.hasText(subscription.getPlan())) {
            subscription.setPlan("UNKNOWN");
        }
        subscription.setStatus(SubscriptionStatus.fromExternal(snapshot.status()));
        subscription.setTrialEndsAt(snapshot.trialEndsAt());
        subscription.setRenewsAt(snapshot.renewsAt());
        subscription.setEndsAt(snapshot.endsAt());
        subscription.setLastProviderEventAt(snapshot.providerUpdatedAt());
        subscriptionRepository.save(subscription);
        planService.synchronizeUserPlan(user);
        return Result.APPLIED;
    }

    private Optional<UserEntity> findUser(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        List<UserEntity> matches = userRepository.findAllByEmail(email.trim().toLowerCase(Locale.ROOT));
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(value);
        }
    }

    public enum Result {
        APPLIED,
        STALE,
        UNMATCHED_USER
    }
}
