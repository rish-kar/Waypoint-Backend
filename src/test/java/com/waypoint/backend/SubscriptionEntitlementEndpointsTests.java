package com.waypoint.backend;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.jwt.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:subscription-entitlement;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
        "subscription-access.admin-email=waypoint-admin@example.com",
        "cors.allowed-origins=http://localhost:5173"
})
class SubscriptionEntitlementEndpointsTests {
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;

    @Autowired
    SubscriptionEntitlementEndpointsTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void cleanDatabase() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void returnsFreeSubscriptionAllowsInstantSearchAndDeniesPremiumFeature() throws Exception {
        UserEntity user = createUser("subscription-test@example.com");

        mockMvc.perform(get("/api/v1/subscriptions/current").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.premium").value(false));

        mockMvc.perform(get("/api/v1/entitlements/features/instant-tab-search").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.plan").value("FREE"));

        mockMvc.perform(get("/api/v1/entitlements/features/ai-summary").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature").value("ai-summary"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void returnsMonthlySubscriptionAndAllowsPremiumFeature() throws Exception {
        UserEntity user = createUser("subscription-monthly@example.com");
        Instant renewsAt = Instant.now().plusSeconds(86400);
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setPlan(CheckoutPlan.MONTHLY.name());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExternalSubscriptionId("sub-monthly");
        subscription.setExternalVariantId("111");
        subscription.setRenewsAt(renewsAt);
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/v1/subscriptions/current").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM_MONTHLY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.externalSubscriptionId").value("sub-monthly"));

        mockMvc.perform(get("/api/v1/entitlements/features/ai-summary").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.plan").value("PREMIUM"));
    }

    @Test
    void configuredAdminEmailGetsAdminStatusAndAllFeatures() throws Exception {
        UserEntity admin = createUser("WAYPOINT-ADMIN@example.com");

        mockMvc.perform(get("/api/v1/subscriptions/current").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ADMIN"))
                .andExpect(jsonPath("$.premium").value(true));

        mockMvc.perform(get("/api/v1/entitlements/features/ai-summary").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.plan").value("ADMIN"));
    }

    @Test
    void rejectsUnknownFeatureWithBadRequest() throws Exception {
        UserEntity user = createUser("subscription-invalid@example.com");

        mockMvc.perform(get("/api/v1/entitlements/features/not-a-feature").header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UserEntity createUser(String email) {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName("Subscription Test");
        user.setProvider("GOOGLE");
        user.setProviderUserId("google-" + user.getId());
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        return userRepository.save(user);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getEmail());
    }
}
