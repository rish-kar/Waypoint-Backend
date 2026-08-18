package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminOverviewResponse;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminOverviewService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final WebhookEventRepository webhookEventRepository;

    public AdminOverviewService(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            WebhookEventRepository webhookEventRepository
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        Instant now = Instant.now();
        Set<UUID> premiumUsers = new HashSet<>(subscriptionRepository.findPremiumUserIds(
                now,
                SubscriptionStatus.ON_TRIAL,
                Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED, SubscriptionStatus.PAST_DUE),
                SubscriptionStatus.CANCELLED
        ));
        premiumUsers.addAll(specialPremiumGrantRepository.findActiveUserIds(now));

        return new AdminOverviewResponse(
                userRepository.count(),
                premiumUsers.size(),
                subscriptionRepository.count(),
                specialPremiumGrantRepository.countActiveAt(now),
                webhookEventRepository.count(),
                webhookEventRepository.countByProcessingStatus(ProcessingStatus.FAILED)
        );
    }
}
