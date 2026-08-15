package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezySubscriptionClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "lemon-squeezy", name = "reconciliation-enabled", havingValue = "true")
public class SubscriptionReconciliationJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);

    private final LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient;
    private final SubscriptionReconciliationService reconciliationService;

    public SubscriptionReconciliationJob(
            LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient,
            SubscriptionReconciliationService reconciliationService
    ) {
        this.lemonSqueezySubscriptionClient = lemonSqueezySubscriptionClient;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            initialDelayString = "${lemon-squeezy.reconciliation-initial-delay-ms:60000}",
            fixedDelayString = "${lemon-squeezy.reconciliation-interval-ms:900000}"
    )
    public void reconcile() {
        List<ProviderSubscriptionSnapshot> snapshots;
        try {
            snapshots = lemonSqueezySubscriptionClient.listSubscriptions();
        } catch (RuntimeException exception) {
            LOGGER.atError()
                    .setCause(exception)
                    .addKeyValue("event", "subscription_reconciliation_fetch_failed")
                    .log("Unable to fetch Lemon Squeezy subscriptions for reconciliation");
            return;
        }

        Map<SubscriptionReconciliationService.Result, Integer> counts =
                new EnumMap<>(SubscriptionReconciliationService.Result.class);
        for (SubscriptionReconciliationService.Result result : SubscriptionReconciliationService.Result.values()) {
            counts.put(result, 0);
        }

        for (ProviderSubscriptionSnapshot snapshot : snapshots) {
            try {
                SubscriptionReconciliationService.Result result = reconciliationService.reconcile(snapshot);
                counts.compute(result, (key, value) -> value == null ? 1 : value + 1);
            } catch (RuntimeException exception) {
                LOGGER.atWarn()
                        .setCause(exception)
                        .addKeyValue("event", "subscription_reconciliation_item_failed")
                        .addKeyValue("external_subscription_id", snapshot.externalSubscriptionId())
                        .log("Unable to reconcile Lemon Squeezy subscription");
            }
        }

        LOGGER.atInfo()
                .addKeyValue("event", "subscription_reconciliation_completed")
                .addKeyValue("fetched", snapshots.size())
                .addKeyValue("applied", counts.get(SubscriptionReconciliationService.Result.APPLIED))
                .addKeyValue("stale", counts.get(SubscriptionReconciliationService.Result.STALE))
                .addKeyValue("unmatched_user", counts.get(SubscriptionReconciliationService.Result.UNMATCHED_USER))
                .log("Subscription reconciliation completed");
    }
}
