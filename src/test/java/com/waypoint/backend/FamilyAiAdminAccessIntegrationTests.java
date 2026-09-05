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
    void specialUserGetsOnlyOwnAbstractQuotaWhileAdminGetsFullPoolAndUsers() throws Exception {
        UserEntity special = createUser("special-family@example.com", "Special Family User");
        SpecialPremiumGrantEntity grant = createGrant(special, 125L * 1_000_000L);
        UserEntity admin = createUser("admin-family@example.com", "Family Admin");

        mockMvc.perform(get("/api/v1/ai/family-usage")
                        .header("Authorization", bearer(special)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialAccess").value(true))
                .andExpect(jsonPath("$.requestTokenLimit").value(5_000))
                .andExpect(jsonPath("$.monthlyAllowanceMicrorupees").value(5_000L * 1_000_000L))
                .andExpect(jsonPath("$.spentMicrorupees").value(125L * 1_000_000L))
                .andExpect(jsonPath("$.remainingMicrorupees").value(4_875L * 1_000_000L))
                .andExpect(jsonPath("$.monthlyPoolMicrorupees").doesNotExist())
                .andExpect(jsonPath("$.activeSpecialUsers").doesNotExist())
                .andExpect(jsonPath("$.users").doesNotExist());

        mockMvc.perform(get("/api/v1/ai/family-admin-usage")
                        .header("Authorization", bearer(special)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/ai/family-admin-usage")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPoolMicrorupees").value(5_000L * 1_000_000L))
                .andExpect(jsonPath("$.poolSpentMicrorupees").value(125L * 1_000_000L))
                .andExpect(jsonPath("$.poolRemainingMicrorupees").value(4_875L * 1_000_000L))
                .andExpect(jsonPath("$.activeSpecialUsers").value(1))
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].grantId").value(grant.getId().toString()))
                .andExpect(jsonPath("$.users[0].userId").value(special.getId().toString()))
                .andExpect(jsonPath("$.users[0].email").value("special-family@example.com"))
                .andExpect(jsonPath("$.users[0].displayName").value("Special Family User"))
                .andExpect(jsonPath("$.users[0].spentMicrorupees").value(125L * 1_000_000L));
    }

    @Test
    void basicAdminEndpointReturnsTheSameFullPoolView() throws Exception {
        UserEntity special = createUser("postman-family@example.com", "Postman Family User");
        createGrant(special, 50L * 1_000_000L);

        mockMvc.perform(get("/api/v1/admin/family-ai")
                        .with(httpBasic("test-admin", "test-admin-password-12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPoolMicrorupees").value(5_000L * 1_000_000L))
                .andExpect(jsonPath("$.poolSpentMicrorupees").value(50L * 1_000_000L))
                .andExpect(jsonPath("$.activeSpecialUsers").value(1))
                .andExpect(jsonPath("$.users[0].email").value("postman-family@example.com"));

        mockMvc.perform(get("/api/v1/admin/family-ai"))
                .andExpect(status().isUnauthorized());
    }

    private UserEntity createUser(String email, String name) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setDisplayName(name);
        user.setProvider("GOOGLE");
        user.setProviderUserId("google-" + email);
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        return userRepository.saveAndFlush(user);
    }

    private SpecialPremiumGrantEntity createGrant(UserEntity user, long spentMicrorupees) {
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setUser(user);
        grant.setActive(true);
        grant.setReason("Friends and family");
        grant.setGrantedBy("test-admin");
        grant.setAiPeriodKey(YearMonth.now(ZoneOffset.UTC).toString());
        grant.setAiSpentMicrorupees(spentMicrorupees);
        grant.setGrantedAt(Instant.now().minusSeconds(60));
        return grantRepository.saveAndFlush(grant);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getEmail());
    }
}
