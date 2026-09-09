package com.waypoint.backend;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.jwt.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:family-ai-admin-access;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "subscription-access.admin-email=admin-family@example.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyAiAdminAccessIntegrationTests {
    private static final long RUPEE = 1_000_000L;
    private static final Instant USER_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant USER_UPDATED_AT = Instant.parse("2025-06-01T12:00:00Z");
    private static final Instant LAST_LOGIN_AT = Instant.parse("2026-08-31T08:30:00Z");
    private static final Instant GRANTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2099-01-01T00:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-02-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository grantRepository;
    private final JwtService jwtService;

    @Autowired
    FamilyAiAdminAccessIntegrationTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository grantRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.grantRepository = grantRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void cleanDatabase() {
        grantRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void specialUserGetsOnlyRollingPercentagesWhileAdminGetsFullPoolAndUsers() throws Exception {
        UserEntity special = createUser("special-family@example.com", "Special Family User");
        SpecialPremiumGrantEntity grant = createGrant(special, 125L * RUPEE);
        UserEntity admin = createUser("admin-family@example.com", "Family Admin");
        String currentPeriod = YearMonth.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/v1/ai/family-usage")
                        .header("Authorization", bearer(special)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialAccess").value(true))
                .andExpect(jsonPath("$.requestTokenLimit").value(5_000))
                .andExpect(jsonPath("$.sessionWindowHours").value(5))
                .andExpect(jsonPath("$.sessionUsagePercent").value(10.0))
                .andExpect(jsonPath("$.sessionResetsAt").isString())
                .andExpect(jsonPath("$.weeklyWindowDays").value(7))
                .andExpect(jsonPath("$.weeklyUsagePercent").value(10.0))
                .andExpect(jsonPath("$.weeklyResetsAt").isString())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.monthlyAllowanceRupees").doesNotExist())
                .andExpect(jsonPath("$.spentRupees").doesNotExist())
                .andExpect(jsonPath("$.remainingRupees").doesNotExist())
                .andExpect(jsonPath("$.monthlyPoolRupees").doesNotExist())
                .andExpect(jsonPath("$.activeSpecialUsers").doesNotExist())
                .andExpect(jsonPath("$.periodKey").doesNotExist())
                .andExpect(jsonPath("$.resetsAt").doesNotExist())
                .andExpect(jsonPath("$.users").doesNotExist());

        mockMvc.perform(get("/api/v1/ai/family-admin-usage")
                        .header("Authorization", bearer(special)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/ai/family-admin-usage")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPoolRupees").value(5_000.0))
                .andExpect(jsonPath("$.poolSpentRupees").value(125.0))
                .andExpect(jsonPath("$.poolRemainingRupees").value(4_875.0))
                .andExpect(jsonPath("$.activeSpecialUsers").value(1))
                .andExpect(jsonPath("$.requestTokenLimit").value(5_000))
                .andExpect(jsonPath("$.sessionWindowHours").value(5))
                .andExpect(jsonPath("$.sessionBudgetPercent").value(5))
                .andExpect(jsonPath("$.weeklyWindowDays").value(7))
                .andExpect(jsonPath("$.weeklyBudgetPercent").value(25))
                .andExpect(jsonPath("$.periodKey").value(currentPeriod))
                .andExpect(jsonPath("$.resetsAt").isString())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].grantId").value(grant.getId().toString()))
                .andExpect(jsonPath("$.users[0].userId").value(special.getId().toString()))
                .andExpect(jsonPath("$.users[0].email").value("special-family@example.com"))
                .andExpect(jsonPath("$.users[0].displayName").value("Special Family User"))
                .andExpect(jsonPath("$.users[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$.users[0].active").value(true))
                .andExpect(jsonPath("$.users[0].monthlyAllowanceRupees").value(5_000.0))
                .andExpect(jsonPath("$.users[0].spentRupees").value(125.0))
                .andExpect(jsonPath("$.users[0].remainingRupees").value(4_875.0))
                .andExpect(jsonPath("$.users[0].monthlyRequestCount").value(12))
                .andExpect(jsonPath("$.users[0].monthlyInputTokens").value(24_000))
                .andExpect(jsonPath("$.users[0].sessionLimitRupees").value(250.0))
                .andExpect(jsonPath("$.users[0].sessionSpentRupees").value(25.0))
                .andExpect(jsonPath("$.users[0].sessionRemainingRupees").value(225.0))
                .andExpect(jsonPath("$.users[0].sessionUsagePercent").value(10.0))
                .andExpect(jsonPath("$.users[0].sessionRequestCount").value(3))
                .andExpect(jsonPath("$.users[0].sessionInputTokens").value(6_000))
                .andExpect(jsonPath("$.users[0].weeklyLimitRupees").value(1_250.0))
                .andExpect(jsonPath("$.users[0].weeklySpentRupees").value(125.0))
                .andExpect(jsonPath("$.users[0].weeklyRemainingRupees").value(1_125.0))
                .andExpect(jsonPath("$.users[0].weeklyUsagePercent").value(10.0))
                .andExpect(jsonPath("$.users[0].weeklyRequestCount").value(8))
                .andExpect(jsonPath("$.users[0].weeklyInputTokens").value(16_000))
                .andExpect(jsonPath("$.users[0].status").value("ACTIVE"));
    }

    @Test
    void basicAdminEndpointReturnsSameCompleteRollingView() throws Exception {
        UserEntity special = createUser("postman-family@example.com", "Postman Family User");
        createGrant(special, 50L * RUPEE);

        mockMvc.perform(get("/api/v1/admin/family-ai")
                        .with(httpBasic("test-admin", "test-admin-password-12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPoolRupees").value(5_000.0))
                .andExpect(jsonPath("$.poolSpentRupees").value(50.0))
                .andExpect(jsonPath("$.sessionWindowHours").value(5))
                .andExpect(jsonPath("$.weeklyWindowDays").value(7))
                .andExpect(jsonPath("$.users[0].sessionUsagePercent").value(10.0))
                .andExpect(jsonPath("$.users[0].weeklyUsagePercent").value(10.0));

        mockMvc.perform(get("/api/v1/admin/family-ai"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminViewKeepsRevokedGrantUsageAndRevocationDetails() throws Exception {
        UserEntity revokedUser = createUser("revoked-family@example.com", "Revoked Family User");
        SpecialPremiumGrantEntity grant = createGrant(revokedUser, 75L * RUPEE);
        grant.setActive(false);
        grant.setRevokedBy("test-admin-revoke");
        grant.setRevokedAt(REVOKED_AT);
        grantRepository.saveAndFlush(grant);

        mockMvc.perform(get("/api/v1/admin/family-ai")
                        .with(httpBasic("test-admin", "test-admin-password-12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolSpentRupees").value(75.0))
                .andExpect(jsonPath("$.activeSpecialUsers").value(0))
                .andExpect(jsonPath("$.users[0].email").value("revoked-family@example.com"))
                .andExpect(jsonPath("$.users[0].active").value(false))
                .andExpect(jsonPath("$.users[0].revokedBy").value("test-admin-revoke"))
                .andExpect(jsonPath("$.users[0].revokedAt").value(REVOKED_AT.toString()))
                .andExpect(jsonPath("$.users[0].monthlyAllowanceRupees").value(0.0))
                .andExpect(jsonPath("$.users[0].spentRupees").value(75.0))
                .andExpect(jsonPath("$.users[0].sessionLimitRupees").value(0.0))
                .andExpect(jsonPath("$.users[0].weeklyLimitRupees").value(0.0))
                .andExpect(jsonPath("$.users[0].status").value("REVOKED"));
    }

    private UserEntity createUser(String email, String name) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setDisplayName(name);
        String slug = email.substring(0, email.indexOf('@'));
        user.setPictureUrl("https://cdn.example.com/" + slug + ".png");
        user.setPhoneNumber("5550101");
        user.setPhoneCountryCode("US");
        user.setProvider("GOOGLE");
        user.setProviderUserId("google-" + email);
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        user.setCreatedAt(USER_CREATED_AT);
        user.setUpdatedAt(USER_UPDATED_AT);
        user.setLastLoginAt(LAST_LOGIN_AT);
        return userRepository.saveAndFlush(user);
    }

    private SpecialPremiumGrantEntity createGrant(UserEntity user, long spentMicrorupees) {
        Instant now = Instant.now();
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setUser(user);
        grant.setActive(true);
        grant.setValidUntil(VALID_UNTIL);
        grant.setReason("Friends and family");
        grant.setGrantedBy("test-admin");
        grant.setAiPeriodKey(YearMonth.now(ZoneOffset.UTC).toString());
        grant.setAiSpentMicrorupees(spentMicrorupees);
        grant.setAiPeriodRequestCount(12L);
        grant.setAiPeriodInputTokens(24_000L);
        grant.setAiSessionStartedAt(now.minusSeconds(60));
        grant.setAiSessionSpentMicrorupees(25L * RUPEE);
        grant.setAiSessionRequestCount(3L);
        grant.setAiSessionInputTokens(6_000L);
        grant.setAiWeeklyStartedAt(now.minusSeconds(60));
        grant.setAiWeeklySpentMicrorupees(125L * RUPEE);
        grant.setAiWeeklyRequestCount(8L);
        grant.setAiWeeklyInputTokens(16_000L);
        grant.setGrantedAt(GRANTED_AT);
        return grantRepository.saveAndFlush(grant);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getEmail());
    }
}
