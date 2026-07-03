package com.waypoint.backend;

import com.waypoint.backend.auth.GoogleProfile;
import com.waypoint.backend.auth.GoogleProfileClient;
import com.waypoint.backend.billing.LemonSqueezyClient;
import com.waypoint.backend.common.UnauthorizedException;
import com.waypoint.backend.security.JwtService;
import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
import com.waypoint.backend.user.UserEntity;
import com.waypoint.backend.user.UserRepository;
import com.waypoint.backend.webhook.ProcessingStatus;
import com.waypoint.backend.webhook.WebhookEventRepository;
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
    private final JwtService jwtService;
    private final FakeGoogleProfileClient googleProfileClient;

    @Autowired
    WaypointBackendApplicationTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            WebhookEventRepository webhookEventRepository,
            JwtService jwtService,
            FakeGoogleProfileClient googleProfileClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookEventRepository = webhookEventRepository;
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
    void protectsJwtEndpoints() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String token = jwtService.issueToken(user.getId(), user.getEmail());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer invalid-token"))
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
    void revokesPremiumOnRefundWebhook() throws Exception {
        UserEntity user = createUser("google-123", "user@example.com");
        String activePayload = subscriptionWebhook(user.getId(), "subscription_created", "sub_refund", "active");
        mockMvc.perform(post("/api/v1/webhooks/lemonsqueezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", hmac(activePayload))
                        .content(activePayload))
                .andExpect(status().isOk());

        String refundPayload = subscriptionWebhook(user.getId(), "order_refunded", "sub_refund", "active");
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
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId("sub_" + UUID.randomUUID());
        subscription.setExternalCustomerId("cus_123");
        subscription.setExternalProductId("prod_123");
        subscription.setExternalVariantId("111");
        subscription.setPlan("MONTHLY");
        subscription.setStatus(status);
        subscription.setRenewsAt(renewsAt);
        return subscriptionRepository.save(subscription);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getEmail());
    }

    private String subscriptionWebhook(UUID userId, String eventName, String subscriptionId, String status) throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "meta": {
                    "event_name": "%s",
                    "custom_data": {
                      "waypoint_user_id": "%s",
                      "waypoint_plan": "MONTHLY"
                    }
                  },
                  "data": {
                    "type": "subscriptions",
                    "id": "%s",
                    "attributes": {
                      "customer_id": "cus_123",
                      "product_id": "prod_123",
                      "variant_id": "111",
                      "status": "%s",
                      "renews_at": "2030-01-01T00:00:00Z",
                      "ends_at": null
                    }
                  }
                }
                """.formatted(eventName, userId, subscriptionId, status));
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
