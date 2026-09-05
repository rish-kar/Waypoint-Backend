package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminOverviewResponse;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminOverviewService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionService subscriptionService;

    public AdminOverviewService(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            WebhookEventRepository webhookEventRepository,
            SubscriptionService subscriptionService
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        Instant now = Instant.now();
        Set<UUID> userIds = userRepository.findAll().stream()
                .map(UserEntity::getId)
                .collect(Collectors.toSet());
        long premiumUsers = subscriptionService.currentForUsers(userIds, now).values().stream()
                .filter(SubscriptionSnapshot::premium)
                .count();

        return new AdminOverviewResponse(
                userRepository.count(),
                premiumUsers,
                subscriptionRepository.count(),
                specialPremiumGrantRepository.countActiveAt(now),
                webhookEventRepository.count(),
                webhookEventRepository.countByProcessingStatus(ProcessingStatus.FAILED)
        );
    }
}
