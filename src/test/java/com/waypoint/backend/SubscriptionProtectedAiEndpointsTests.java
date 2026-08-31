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
        "spring.datasource.url=jdbc:h2:mem:subscription-protected-ai;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
    void anonymousUserCannotCallPremiumAi() throws Exception {
        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void freeUserCannotCallPremiumAi() throws Exception {
        UserEntity user = createUser();

        mockMvc.perform(get("/api/v1/ai/models").header("Authorization", bearer(user)))
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
