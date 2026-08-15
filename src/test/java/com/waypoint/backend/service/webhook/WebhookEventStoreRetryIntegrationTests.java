package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebhookEventStoreRetryIntegrationTests {
    private final WebhookEventStore webhookEventStore;
    private final WebhookEventRepository webhookEventRepository;

    @Autowired
    WebhookEventStoreRetryIntegrationTests(
            WebhookEventStore webhookEventStore,
            WebhookEventRepository webhookEventRepository
    ) {
        this.webhookEventStore = webhookEventStore;
        this.webhookEventRepository = webhookEventRepository;
    }

    @BeforeEach
    void cleanDatabase() {
        webhookEventRepository.deleteAll();
    }

    @Test
    void failedEventIsReclaimedForRetry() {
        String eventHash = "failed-retry-event-hash";
        WebhookEventStore.WebhookReception first = webhookEventStore.recordReceived(eventHash, "{\"ok\":true}");
        webhookEventStore.markFailed(eventHash, "subscription_updated", "sub_retry", "first failure");

        var failed = webhookEventRepository.findById(first.event().getId()).orElseThrow();
        assertThat(failed.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getErrorMessage()).isEqualTo("first failure");
        assertThat(failed.getProcessedAt()).isNotNull();

        WebhookEventStore.WebhookReception retry = webhookEventStore.recordReceived(eventHash, "{\"ok\":true}");

        assertThat(retry.created()).isFalse();
        assertThat(retry.shouldProcess()).isTrue();
        assertThat(retry.event().getProcessingStatus()).isEqualTo(ProcessingStatus.RECEIVED);
        assertThat(retry.event().getAttemptCount()).isEqualTo(2);
        assertThat(retry.event().getErrorMessage()).isNull();
        assertThat(retry.event().getProcessedAt()).isNull();
        assertThat(retry.event().getLastAttemptAt()).isNotNull();
    }
}
