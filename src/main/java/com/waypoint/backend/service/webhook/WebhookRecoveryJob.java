package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.service.subscription.SubscriptionReconciliationService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezySubscriptionClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "lemon-squeezy", name = "reconciliation-enabled", havingValue = "true")
public class WebhookRecoveryJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookRecoveryJob.class);

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventStore webhookEventStore;
    private final LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient;
    private final SubscriptionReconciliationService reconciliationService;

    public WebhookRecoveryJob(
            WebhookEventRepository webhookEventRepository,
            WebhookEventStore webhookEventStore,
            LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient,
            SubscriptionReconciliationService reconciliationService
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.webhookEventStore = webhookEventStore;
        this.lemonSqueezySubscriptionClient = lemonSqueezySubscriptionClient;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            initialDelayString = "${lemon-squeezy.webhook-recovery-initial-delay-ms:60000}",
            fixedDelayString = "${lemon-squeezy.webhook-recovery-interval-ms:60000}"
    )
    public void recoverAbandonedEvents() {
        Instant cutoff = Instant.now().minus(WebhookEventStore.STALE_RECEIVED_AFTER);
        List<WebhookEventEntity> candidates = webhookEventRepository
                .findTop100ByProcessingStatusAndLastAttemptAtBeforeOrderByLastAttemptAtAsc(
                        ProcessingStatus.RECEIVED,
                        cutoff
                );
        if (candidates.isEmpty()) {
            return;
        }

        Map<String, ProviderSubscriptionSnapshot> providerSubscriptions;
        try {
            providerSubscriptions = lemonSqueezySubscriptionClient.listSubscriptions().stream()
                    .filter(snapshot -> StringUtils.hasText(snapshot.externalSubscriptionId()))
                    .collect(Collectors.toMap(
                            ProviderSubscriptionSnapshot::externalSubscriptionId,
                            Function.identity(),
                            (left, right) -> right
                    ));
        } catch (RuntimeException exception) {
            LOGGER.atError()
                    .setCause(exception)
                    .addKeyValue("event", "webhook_recovery_provider_fetch_failed")
                    .log("Unable to load provider state for webhook recovery");
            return;
        }

        for (WebhookEventEntity candidate : candidates) {
            recover(candidate, providerSubscriptions);
        }
    }

    private void recover(
            WebhookEventEntity candidate,
            Map<String, ProviderSubscriptionSnapshot> providerSubscriptions
    ) {
        WebhookEventStore.RecoveryClaim claim = webhookEventStore.claimForRecovery(candidate.getId());
        if (claim == null) {
            return;
        }
        if (!StringUtils.hasText(claim.externalObjectId())) {
            webhookEventStore.markFailed(
                    claim.eventHash(),
                    claim.eventName(),
                    null,
                    "Abandoned webhook cannot be recovered because subscription identity is missing"
            );
            return;
        }

        ProviderSubscriptionSnapshot snapshot = providerSubscriptions.get(claim.externalObjectId());
        if (snapshot == null) {
            LOGGER.atWarn()
                    .addKeyValue("event", "webhook_recovery_deferred")
                    .addKeyValue("external_subscription_id", claim.externalObjectId())
                    .log("Provider subscription is not yet available; webhook recovery will retry");
            return;
        }

        try {
            SubscriptionReconciliationService.Result result = reconciliationService.reconcile(snapshot);
            if (result == SubscriptionReconciliationService.Result.UNMATCHED_USER) {
                LOGGER.atWarn()
                        .addKeyValue("event", "webhook_recovery_deferred")
                        .addKeyValue("external_subscription_id", claim.externalObjectId())
                        .addKeyValue("reason", "unmatched_user")
                        .log("Webhook recovery will retry after the local user becomes available");
                return;
            }

            webhookEventStore.markProcessed(claim.eventHash(), claim.eventName(), claim.externalObjectId());
            LOGGER.atInfo()
                    .addKeyValue("event", "webhook_recovered")
                    .addKeyValue("external_subscription_id", claim.externalObjectId())
                    .addKeyValue("reconciliation_result", result)
                    .log("Abandoned webhook recovered from provider state");
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .setCause(exception)
                    .addKeyValue("event", "webhook_recovery_attempt_failed")
                    .addKeyValue("external_subscription_id", claim.externalObjectId())
                    .log("Webhook recovery attempt failed and will be retried");
        }
    }
}