package com.waypoint.backend;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDataManagementIntegrationTests {
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final AdminAuditEventRepository adminAuditEventRepository;

    @Autowired
    AdminDataManagementIntegrationTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            WebhookEventRepository webhookEventRepository,
            AdminAuditEventRepository adminAuditEventRepository
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.adminAuditEventRepository = adminAuditEventRepository;
    }

    @BeforeEach
    void cleanDatabase() {
        adminAuditEventRepository.deleteAll();
        specialPremiumGrantRepository.deleteAll();
        subscriptionRepository.deleteAll();
        webhookEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listsFiltersAndSummarizesAdminData() throws Exception {
        UserEntity alice = createUser("alice@example.com", "Alice", "alice-google");
        createUser("bob@example.com", "Bob", "bob-google");
        SubscriptionEntity subscription = createMonthlySubscription(alice, SubscriptionStatus.ACTIVE);
        WebhookEventEntity webhook = createWebhook("subscription_updated", ProcessingStatus.PROCESSED);

        mockMvc.perform(get("/api/v1/admin/overview").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(2))
                .andExpect(jsonPath("$.subscriptions").value(1))
                .andExpect(jsonPath("$.webhookEvents").value(1));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "alice")
                        .param("size", "10")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("alice@example.com"));

        mockMvc.perform(get("/api/v1/admin/subscriptions")
                        .param("status", "ACTIVE")
                        .param("email", "alice@example.com")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(subscription.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/webhook-events")
                        .param("processingStatus", "PROCESSED")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(webhook.getId().toString()))
                .andExpect(jsonPath("$.items[0].payloadJson").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/webhook-events/{eventId}", webhook.getId())
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payloadJson").value("{\"test\":true}"));

        mockMvc.perform(get("/api/v1/admin/plans").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'PREMIUM_SPECIAL')]").isNotEmpty());
    }

    @Test
    void updatesSubscriptionAndWritesAuditEvent() throws Exception {
        UserEntity user = createUser("paid@example.com", "Paid User", "paid-google");
        SubscriptionEntity subscription = createMonthlySubscription(user, SubscriptionStatus.INACTIVE);

        mockMvc.perform(patch("/api/v1/admin/subscriptions/{subscriptionId}", subscription.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE",
                                  "renewsAt": "2027-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/admin/users/{userId}", user.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM_MONTHLY"))
                .andExpect(jsonPath("$.premium").value(true));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "UPDATE_SUBSCRIPTION")
                        .param("resourceId", subscription.getId().toString())
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].adminId").value("test-admin"));
    }

    @Test
    void updatesWebhookMetadataAndAuditsChange() throws Exception {
        WebhookEventEntity webhook = createWebhook("subscription_updated", ProcessingStatus.FAILED);
        webhook.setErrorMessage("old error");
        webhookEventRepository.saveAndFlush(webhook);

        mockMvc.perform(patch("/api/v1/admin/webhook-events/{eventId}", webhook.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingStatus": "PROCESSED",
                                  "clearErrorMessage": true,
                                  "processedAt": "2026-08-13T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "UPDATE_WEBHOOK_EVENT")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void validatesPaginationAndSortFields() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "501")
                        .with(admin()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("sort", "providerUserId")
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor admin() {
        return httpBasic("test-admin", "test-admin-password-12345");
    }

    private UserEntity createUser(String email, String displayName, String providerUserId) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setProvider("GOOGLE");
        user.setProviderUserId(providerUserId);
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        return userRepository.saveAndFlush(user);
    }

    private SubscriptionEntity createMonthlySubscription(UserEntity user, SubscriptionStatus status) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalCustomerId("customer-" + user.getId());
        subscription.setExternalSubscriptionId("subscription-" + user.getId());
        subscription.setExternalProductId("product-1");
        subscription.setExternalVariantId("111");
        subscription.setPlan("MONTHLY");
        subscription.setStatus(status);
        subscription.setRenewsAt(Instant.parse("2027-01-01T00:00:00Z"));
        return subscriptionRepository.saveAndFlush(subscription);
    }

    private WebhookEventEntity createWebhook(String eventName, ProcessingStatus status) {
        WebhookEventEntity event = new WebhookEventEntity();
        event.setEventHash("hash-" + java.util.UUID.randomUUID());
        event.setEventName(eventName);
        event.setExternalObjectId("external-1");
        event.setProcessingStatus(status);
        event.setPayloadJson("{\"test\":true}");
        event.setReceivedAt(Instant.now());
        if (status == ProcessingStatus.PROCESSED) {
            event.setProcessedAt(Instant.now());
        }
        return webhookEventRepository.saveAndFlush(event);
    }
}
