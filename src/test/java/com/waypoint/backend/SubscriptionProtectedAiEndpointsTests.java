package com.waypoint.backend;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:subscription-protected-ai;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionProtectedAiEndpointsTests {
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final JwtService jwtService;

    @Autowired
    SubscriptionProtectedAiEndpointsTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            PlanRepository planRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void modelCatalogueRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCannotReadFamilyUsage() throws Exception {
        mockMvc.perform(get("/api/v1/ai/family-usage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void freeUserGetsOnlyZeroedPersonalFamilyUsage() throws Exception {
        UserEntity user = createUser();

        mockMvc.perform(get("/api/v1/ai/family-usage")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialAccess").value(false))
                .andExpect(jsonPath("$.status").value("NOT_SPECIAL"))
                .andExpect(jsonPath("$.requestTokenLimit").value(0))
                .andExpect(jsonPath("$.monthlyAllowanceRupees").value(0.0))
                .andExpect(jsonPath("$.spentRupees").value(0.0))
                .andExpect(jsonPath("$.remainingRupees").value(0.0))
                .andExpect(jsonPath("$.monthlyAllowanceMicrorupees").doesNotExist())
                .andExpect(jsonPath("$.spentMicrorupees").doesNotExist())
                .andExpect(jsonPath("$.remainingMicrorupees").doesNotExist())
                .andExpect(jsonPath("$.monthlyPoolRupees").doesNotExist())
                .andExpect(jsonPath("$.activeSpecialUsers").doesNotExist())
                .andExpect(jsonPath("$.users").doesNotExist());
    }

    @Test
    void normalUserCannotReadFamilyAdminUsage() throws Exception {
        UserEntity user = createUser();

        mockMvc.perform(get("/api/v1/ai/family-admin-usage")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void anonymousUserCannotCallPremiumAi() throws Exception {
        mockMvc.perform(post("/api/v1/ai/intent")
                        .contentType("application/json")
                        .content("{\"request\":\"group my tabs\",\"lastSelectionAvailable\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void freeUserCannotCallPremiumAi() throws Exception {
        UserEntity user = createUser();

        mockMvc.perform(post("/api/v1/ai/intent")
                        .header("Authorization", bearer(user))
                        .contentType("application/json")
                        .content("{\"request\":\"group my tabs\",\"lastSelectionAvailable\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
    }

    private UserEntity createUser() {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("free-ai-user@example.com");
        user.setDisplayName("Free AI User");
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
