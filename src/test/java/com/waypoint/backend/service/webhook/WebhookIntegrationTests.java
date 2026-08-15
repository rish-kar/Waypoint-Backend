package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookIntegrationTests {
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventStore webhookEventStore;

    @Autowired
    WebhookIntegrationTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            WebhookEventRepository webhookEventRepository,
            WebhookEventStore webhookEventStore
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookEventStore = webhookEventStore;
    }

    @BeforeEach
    void cleanDatabase() {
        webhookEventRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rejectsMalformedHexSignatureBeforePersistingEvent() throws Exception {
        String payload = "{\"meta\":{\"event_name\":\"subscription_updated\"}}";

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "not-hex")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(webhookEventRepository.findAll()).isEmpty();
    }

    @Test
    void recordsUnsupportedSignedEventAsIgnored() throws Exception {
        String payload = objectMapper.writeValueAsString(objectMapper.readTree("""
                {
                  "meta": {"event_name": "order_created"},
                  "data": {"type": "orders", "id": "order_1", "attributes": {"store_id": 123}}
                }
                """));

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(subscriptionRepository.findAll()).isEmpty();
        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventName()).isEqualTo("order_created");
                    assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.IGNORED);
                });
    }

    @Test
    void storesLemonSqueezyTrialEndDateForOnTrialSubscription() throws Exception {
        UserEntity user = createUser();
        String trialEndsAt = "2030-01-08T00:00:00Z";
        JsonNode node = objectMapper.readTree("""
                {
                  "meta": {
                    "event_name": "subscription_created",
                    "custom_data": {
                      "waypoint_user_id": "%s",
                      "waypoint_plan": "MONTHLY"
                    }
                  },
                  "data": {
                    "type": "subscriptions",
                    "id": "sub_trial",
                    "attributes": {
                      "store_id": 123,
                      "customer_id": 123,
                      "product_id": 456,
                      "variant_id": 111,
                      "status": "on_trial",
                      "trial_ends_at": "%s",
                      "renews_at": "%s",
                      "ends_at": null
                    }
                  }
                }
                """.formatted(user.getId(), trialEndsAt, trialEndsAt));
        String payload = objectMapper.writeValueAsString(node);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        SubscriptionEntity subscription = subscriptionRepository
                .findByExternalSubscriptionId("sub_trial")
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ON_TRIAL);
        assertThat(subscription.getTrialEndsAt()).isEqualTo(Instant.parse(trialEndsAt));
        assertThat(subscription.getRenewsAt()).isEqualTo(Instant.parse(trialEndsAt));
    }

    @Test
    void storesPastDueSubscriptionStatus() throws Exception {
        UserEntity user = createUser();
        String payload = subscriptionWebhook(user.getId(), "sub_past_due", "past_due", "123");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        SubscriptionEntity subscription = subscriptionRepository
                .findByExternalSubscriptionId("sub_past_due")
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void rejectsWebhookFromUnexpectedStoreAndPersistsFailure() throws Exception {
        UserEntity user = createUser();
        String payload = subscriptionWebhook(user.getId(), "sub_wrong_store", "active", "999");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(subscriptionRepository.findByExternalSubscriptionId("sub_wrong_store")).isEmpty();
        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
                    assertThat(event.getErrorMessage()).contains("unexpected Lemon Squeezy store");
                });
    }

    @Test
    void freshReceivedDuplicateIsNotReprocessed() {
        WebhookEventStore.WebhookReception first = webhookEventStore.recordReceived("fresh-event-hash", "{\"ok\":true}");
        WebhookEventStore.WebhookReception second = webhookEventStore.recordReceived("fresh-event-hash", "{\"ok\":true}");

        assertThat(first.created()).isTrue();
        assertThat(first.shouldProcess()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.shouldProcess()).isFalse();
        assertThat(second.event().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void staleReceivedEventCanBeReclaimedAfterInterruptedProcessing() {
        WebhookEventStore.WebhookReception first = webhookEventStore.recordReceived("stale-event-hash", "{\"ok\":true}");
        var event = webhookEventRepository.findById(first.event().getId()).orElseThrow();
        event.setLastAttemptAt(Instant.now().minusSeconds(600));
        webhookEventRepository.saveAndFlush(event);

        WebhookEventStore.WebhookReception retry = webhookEventStore.recordReceived("stale-event-hash", "{\"ok\":true}");

        assertThat(retry.created()).isFalse();
        assertThat(retry.shouldProcess()).isTrue();
        assertThat(retry.event().getAttemptCount()).isEqualTo(2);
        assertThat(retry.event().getProcessingStatus()).isEqualTo(ProcessingStatus.RECEIVED);
    }

    private UserEntity createUser() {
        UserEntity user = new UserEntity();
        user.setProvider("GOOGLE");
        user.setProviderUserId("google-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setDisplayName("Webhook Test User");
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    private String subscriptionWebhook(UUID userId, String subscriptionId, String status, String storeId) throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "meta": {
                    "event_name": "subscription_updated",
                    "custom_data": {
                      "waypoint_user_id": "%s",
                      "waypoint_plan": "MONTHLY"
                    }
                  },
                  "data": {
                    "type": "subscriptions",
                    "id": "%s",
                    "attributes": {
                      "store_id": %s,
                      "customer_id": 123,
                      "product_id": 456,
                      "variant_id": 111,
                      "status": "%s",
                      "renews_at": "2030-01-01T00:00:00Z",
                      "ends_at": null
                    }
                  }
                }
                """.formatted(userId, subscriptionId, storeId, status));
        return objectMapper.writeValueAsString(node);
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
