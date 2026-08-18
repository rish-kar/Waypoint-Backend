package com.waypoint.backend;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.security.jwt.JwtService;
import com.waypoint.backend.service.webhook.WebhookEventStore;
import com.waypoint.backend.utilities.client.google.GoogleProfileClient;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:waypoint;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret=test-secret-that-is-long-enough-for-hmac",
        "jwt.expiration-seconds=86400",
        "google.client-id=test-google-client",
        "lemon-squeezy.api-key=test-api-key",
        "lemon-squeezy.store-id=123",
        "lemon-squeezy.monthly-variant-id=111",
        "lemon-squeezy.annual-variant-id=222",
        "lemon-squeezy.webhook-secret=test-webhook-secret",
        "cors.allowed-origins=http://localhost:5173"
})
class WaypointBackendApplicationTests {
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventStore webhookEventStore;
    private final JwtService jwtService;
    private final FakeGoogleProfileClient googleProfileClient;

    @Autowired
    WaypointBackendApplicationTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            WebhookEventRepository webhookEventRepository,
            WebhookEventStore webhookEventStore,
            JwtService jwtService,
            FakeGoogleProfileClient googleProfileClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookEventStore = webhookEventStore;
        this.jwtService = jwtService;
        this.googleProfileClient = googleProfileClient;
    }

    @BeforeEach
    void cleanDatabase() {
        webhookEventRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        googleProfileClient.profile = new GoogleProfile(
                "google-123",
                "USER@Example.com",
                true,
                "User Name",
                "https://example.com/picture.png",
                "test-google-client"
        );
        googleProfileClient.fail = false;
    }

    @Test
    void createsNewGoogleUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.entitlement.plan").value("FREE"))
                .andExpect(jsonPath("$.entitlement.premium").value(false))
                .andExpect(jsonPath("$.entitlement.features[0]").value("instant-tab-search"));

        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userRepository.findAll().getFirst().getProviderUserId()).isEqualTo("google-123");
    }

    @Test
    void logsInExistingGoogleUserWithoutDuplicating() throws Exception {
        UserEntity existing = createUser("google-123", "old@example.com");
        Instant previousLogin = existing.getLastLoginAt();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.user.email").value("user@example.com"));

        UserEntity updated = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(updated.getLastLoginAt()).isAfter(previousLogin);
    }

    @Test
    void rejectsInvalidGoogleToken() throws Exception {
        googleProfileClient.fail = true;

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"bad-google-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsGoogleProfileWithoutAudience() throws Exception {
        googleProfileClient.profile = new GoogleProfile(
                "google-123",
                "user@example.com",
                true,
                "User Name",
                "https://example.com/picture.png",
                null
        );

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsGoogleProfileWithMismatchedAudience() throws Exception {
        googleProfileClient.profile = new GoogleProfile(
                "google-123",
                "user@example.com",
                true,
                "User Name",
                "https://example.com/picture.png",
                "different-client"
        );

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void acceptsGoogleProfileWithMatchingAudience() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
    }

    @Test
    void protectsJwtEndpoints() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String token = jwtService.issueToken(user.getId(), user.getEmail());

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsFreeEntitlementWithoutSubscription() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.checkedAt").isString());
    }

    @Test
    void returnsActivePremiumEntitlement() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(86400));

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.features[8]").value("ai-summary"));
    }

    @Test
    void allowsCancelledSubscriptionBeforeEndsAt() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        SubscriptionEntity subscription = createSubscription(user, SubscriptionStatus.CANCELLED, null);
        subscription.setEndsAt(Instant.now().plusSeconds(3600));
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.premium").value(true));
    }

    @Test
    void billingStatusAllowsCancelledSubscriptionBeforeEndsAt() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        SubscriptionEntity subscription = createSubscription(user, SubscriptionStatus.CANCELLED, null);
        subscription.setEndsAt(Instant.now().plusSeconds(3600));
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/v1/billing/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void billingStatusReturnsFreeForCancelledSubscriptionAfterEndsAt() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        SubscriptionEntity subscription = createSubscription(user, SubscriptionStatus.CANCELLED, null);
        subscription.setEndsAt(Instant.now().minusSeconds(3600));
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/v1/billing/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void billingStatusReturnsFreeForCancelledSubscriptionWithNullEndsAt() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.CANCELLED, null);

        mockMvc.perform(get("/api/v1/billing/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void billingStatusReturnsFreeForRefundedSubscription() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.REFUNDED, null);

        mockMvc.perform(get("/api/v1/billing/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void returnsFreeForExpiredSubscription() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.EXPIRED, null);

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void acceptsValidLemonSqueezyWebhookSignature() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String payload = subscriptionWebhook(user.getId(), "subscription_created", "sub_valid", "active");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(webhookEventRepository.findAll().getFirst().getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
    }

    @Test
    void rejectsInvalidWebhookSignature() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String payload = subscriptionWebhook(user.getId(), "subscription_created", "sub_invalid", "active");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "bad-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(webhookEventRepository.findAll()).isEmpty();
    }

    @Test
    void handlesDuplicateWebhookIdempotently() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String payload = subscriptionWebhook(user.getId(), "subscription_updated", "sub_duplicate", "active");
        String signature = hmac(payload);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(subscriptionRepository.findAll()).hasSize(1);
    }

    @Test
    void duplicateWebhookRaceCreatesOnlyOneEventRecord() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<WebhookEventStore.WebhookReception> task = () -> webhookEventStore.recordReceived("race-hash", "{\"ok\":true}");
            List<Future<WebhookEventStore.WebhookReception>> futures = executor.invokeAll(List.of(task, task));

            List<WebhookEventStore.WebhookReception> receptions = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(receptions.stream().filter(WebhookEventStore.WebhookReception::created)).hasSize(1);
            assertThat(webhookEventRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void createsSubscriptionFromWebhookCustomData() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String payload = subscriptionWebhook(user.getId(), "subscription_created", "sub_created", "active");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        SubscriptionEntity subscription = subscriptionRepository.findByExternalSubscriptionId("sub_created").orElseThrow();
        assertThat(subscription.getUser().getId()).isEqualTo(user.getId());
        assertThat(subscription.getExternalCustomerId()).isEqualTo("cus_123");
        assertThat(subscription.getExternalVariantId()).isEqualTo("111");
        assertThat(subscription.getPlan()).isEqualTo("MONTHLY");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void activeUnknownVariantRemainsFree() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String payload = subscriptionWebhook(user.getId(), "subscription_created", "sub_unknown_variant", "active", "999", true, null);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        SubscriptionEntity subscription = subscriptionRepository.findByExternalSubscriptionId("sub_unknown_variant").orElseThrow();
        assertThat(subscription.getPlan()).isEqualTo("UNKNOWN");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));
        mockMvc.perform(get("/api/v1/billing/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updatesExistingSubscriptionWithoutCustomData() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.CANCELLED, null, "sub_existing", "111", "MONTHLY");
        String payload = subscriptionWebhook(null, "subscription_updated", "sub_existing", "active", "111", false, null);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        SubscriptionEntity subscription = subscriptionRepository.findByExternalSubscriptionId("sub_existing").orElseThrow();
        assertThat(subscription.getUser().getId()).isEqualTo(user.getId());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void refundsExistingSubscriptionWithoutCustomData() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600), "sub_refund_no_custom", "111", "MONTHLY");
        String payload = subscriptionPaymentRefundedWebhook(null, "sub_refund_no_custom", false);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(subscriptionRepository.findByExternalSubscriptionId("sub_refund_no_custom").orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.REFUNDED);
    }

    @Test
    void retriesFailedWebhookDeliveryAfterExistingSubscriptionAppears() throws Exception {
        String payload = subscriptionWebhook(null, "subscription_updated", "sub_retry_failed", "active", "111", false, null);
        String signature = hmac(payload);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED));

        UserEntity user = createUser("google-123", "user@example.com");
        createSubscription(user, SubscriptionStatus.CANCELLED, null, "sub_retry_failed", "111", "MONTHLY");

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED));
        assertThat(subscriptionRepository.findByExternalSubscriptionId("sub_retry_failed").orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void rejectsNewSubscriptionWithoutCustomDataAndStoresFailedEvent() throws Exception {
        String payload = subscriptionWebhook(null, "subscription_created", "sub_missing_custom", "active", "111", false, null);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(subscriptionRepository.findByExternalSubscriptionId("sub_missing_custom")).isEmpty();
        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
                    assertThat(event.getPayloadJson())
                            .contains("\"redacted\":true")
                            .doesNotContain("sub_missing_custom");
                    assertThat(event.getErrorMessage()).contains("waypoint_user_id");
                });
    }

    @Test
    void rejectsExistingSubscriptionConflictingCustomUserAndPreservesFailure() throws Exception {
        UserEntity owner = createUser("google-owner", "owner@example.com");
        UserEntity other = createUser("google-other", "other@example.com");
        createSubscription(owner, SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600), "sub_conflict", "111", "MONTHLY");
        String payload = subscriptionWebhook(other.getId(), "subscription_updated", "sub_conflict", "active", "111", true, null);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());

        SubscriptionEntity subscription = subscriptionRepository.findByExternalSubscriptionId("sub_conflict").orElseThrow();
        assertThat(subscription.getUser().getId()).isEqualTo(owner.getId());
        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED));
    }

    @Test
    void unknownWebhookUserDoesNotCreateSubscriptionAndFailureRemainsStored() throws Exception {
        String payload = subscriptionWebhook(UUID.randomUUID(), "subscription_created", "sub_unknown_user", "active", "111", true, null);

        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(subscriptionRepository.findByExternalSubscriptionId("sub_unknown_user")).isEmpty();
        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
                    assertThat(event.getErrorMessage()).contains("unknown Waypoint user");
                });
    }

    @Test
    void revokesPremiumOnRefundWebhook() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String activePayload = subscriptionWebhook(user.getId(), "subscription_created", "sub_refund", "active");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(activePayload))
                        .content(activePayload))
                .andExpect(status().isOk());

        String refundPayload = subscriptionPaymentRefundedWebhook(user.getId(), "sub_refund", true);
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(refundPayload))
                        .content(refundPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void rejectsInvalidCheckoutPlan() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"WEEKLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UserEntity createUser(String providerUserId, String email) {
        UserEntity user = new UserEntity();
        user.setProvider("GOOGLE");
        user.setProviderUserId(providerUserId);
        user.setEmail(email);
        user.setDisplayName("User Name");
        user.setPictureUrl("https://example.com/picture.png");
        user.setLastLoginAt(Instant.now().minusSeconds(60));
        return userRepository.save(user);
    }

    private SubscriptionEntity createSubscription(UserEntity user, SubscriptionStatus status, Instant renewsAt) {
        return createSubscription(user, status, renewsAt, "sub_" + UUID.randomUUID(), "111", "MONTHLY");
    }

    private SubscriptionEntity createSubscription(
            UserEntity user,
            SubscriptionStatus status,
            Instant renewsAt,
            String externalSubscriptionId,
            String variantId,
            String plan
    ) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId(externalSubscriptionId);
        subscription.setExternalCustomerId("cus_123");
        subscription.setExternalProductId("prod_123");
        subscription.setExternalVariantId(variantId);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setRenewsAt(renewsAt);
        return subscriptionRepository.save(subscription);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getEmail());
    }

    private String subscriptionWebhook(UUID userId, String eventName, String subscriptionId, String status) throws Exception {
        return subscriptionWebhook(userId, eventName, subscriptionId, status, "111", true, null);
    }

    private String subscriptionWebhook(
            UUID userId,
            String eventName,
            String subscriptionId,
            String status,
            String variantId,
            boolean includeCustomData,
            Instant endsAt
    ) throws Exception {
        String customData = includeCustomData ? """
                    "custom_data": {
                      "waypoint_user_id": "%s",
                      "waypoint_plan": "MONTHLY"
                    }
                """.formatted(userId) : "\"custom_data\": {}";
        String endsAtJson = endsAt == null ? "null" : "\"" + endsAt + "\"";
        JsonNode node = objectMapper.readTree("""
                {
                  "meta": {
                    "event_name": "%s",
                    %s
                  },
                  "data": {
                    "type": "subscriptions",
                    "id": "%s",
                    "attributes": {
                      "customer_id": "cus_123",
                      "product_id": "prod_123",
                      "variant_id": "%s",
                      "status": "%s",
                      "renews_at": "2030-01-01T00:00:00Z",
                      "ends_at": %s
                    }
                  }
                }
                """.formatted(eventName, customData, subscriptionId, variantId, status, endsAtJson));
        return objectMapper.writeValueAsString(node);
    }

    private String subscriptionPaymentRefundedWebhook(UUID userId, String subscriptionId, boolean includeCustomData) throws Exception {
        String customData = includeCustomData ? """
                    "custom_data": {
                      "waypoint_user_id": "%s",
                      "waypoint_plan": "MONTHLY"
                    }
                """.formatted(userId) : "\"custom_data\": {}";
        JsonNode node = objectMapper.readTree("""
                {
                  "meta": {
                    "event_name": "subscription_payment_refunded",
                    %s
                  },
                  "data": {
                    "type": "subscription-invoices",
                    "id": "subinv_%s",
                    "attributes": {
                      "subscription_id": "%s",
                      "customer_id": "cus_123",
                      "status": "refunded"
                    }
                  }
                }
                """.formatted(customData, subscriptionId, subscriptionId));
        return objectMapper.writeValueAsString(node);
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        FakeGoogleProfileClient fakeGoogleProfileClient() {
            return new FakeGoogleProfileClient();
        }

        @Bean
        @Primary
        LemonSqueezyClient lemonSqueezyClient() {
            return (user, plan, variantId) -> "https://checkout.example/" + plan.name().toLowerCase();
        }
    }

    static class FakeGoogleProfileClient implements GoogleProfileClient {
        GoogleProfile profile;
        boolean fail;

        @Override
        public GoogleProfile fetchProfile(String accessToken) {
            if (fail) {
                throw new UnauthorizedException("Invalid Google access token");
            }
            return profile;
        }
    }
}