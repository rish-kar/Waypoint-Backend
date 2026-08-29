package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.entitlement.FeatureCatalog;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.ApiException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AiUsageService {
    public static final int TRIAL_REQUEST_LIMIT = 20;

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureCatalog featureCatalog;

    public AiUsageService(
            UserRepository userRepository,
            SubscriptionService subscriptionService,
            FeatureCatalog featureCatalog
    ) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.featureCatalog = featureCatalog;
    }

    @Transactional(readOnly = true)
    public AiUsageResponse current(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        return response(subscription, user.getAiTrialRequestsUsed());
    }

    @Transactional
    public AiUsageResponse consume(UUID userId) {
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        requireCloudAiAccess(subscription);

        if (subscription.status() != SubscriptionStatus.ON_TRIAL) {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            return response(subscription, user.getAiTrialRequestsUsed());
        }

        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        int used = Math.max(0, user.getAiTrialRequestsUsed());
        if (used >= TRIAL_REQUEST_LIMIT) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_TRIAL_LIMIT_REACHED",
                    "Your 20 Cloud AI trial requests have been used."
            );
        }

        user.setAiTrialRequestsUsed(used + 1);
        userRepository.save(user);
        return response(subscription, used + 1);
    }

    private void requireCloudAiAccess(SubscriptionSnapshot subscription) {
        if (!cloudAiEntitled(subscription)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AI_ACCESS_DENIED",
                    "Cloud AI is not available for this account."
            );
        }
    }

    private AiUsageResponse response(SubscriptionSnapshot subscription, int storedUsed) {
        int used = Math.max(0, storedUsed);
        boolean trialLimited = subscription.premium() && subscription.status() == SubscriptionStatus.ON_TRIAL;
        int remaining = Math.max(0, TRIAL_REQUEST_LIMIT - used);
        boolean allowed = cloudAiEntitled(subscription) && (!trialLimited || remaining > 0);
        return new AiUsageResponse(
                allowed,
                trialLimited,
                TRIAL_REQUEST_LIMIT,
                used,
                remaining,
                subscription.status().name()
        );
    }

    private boolean cloudAiEntitled(SubscriptionSnapshot subscription) {
        return subscription.premium()
                && featureCatalog.hasFeature(subscription.planCode(), FeatureCode.AI_SUMMARY);
    }
}
