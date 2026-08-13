package com.waypoint.backend;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPremiumSpecialIntegrationTests {
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final JwtService jwtService;

    @Autowired
    AdminPremiumSpecialIntegrationTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void cleanDatabase() {
        specialPremiumGrantRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCredentialsAreRequired() throws Exception {
        UserEntity user = createUser();

        mockMvc.perform(put("/api/v1/admin/users/{userId}/premium-special", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Friends and family\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/admin/users/{userId}/premium-special", user.getId())
                        .with(httpBasic("test-admin", "wrong-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Friends and family\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void grantsListsAndRevokesPremiumSpecial() throws Exception {
        UserEntity user = createUser();
        String jwt = jwtService.issueToken(user.getId(), user.getEmail());

        mockMvc.perform(put("/api/v1/admin/users/{userId}/premium-special", user.getId())
                        .with(httpBasic("test-admin", "test-admin-password-12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Friends and family\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM_SPECIAL"))
                .andExpect(jsonPath("$.status").value("PREMIUM_SPECIAL"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/subscriptions/current")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM_SPECIAL"))
                .andExpect(jsonPath("$.status").value("PREMIUM_SPECIAL"))
                .andExpect(jsonPath("$.premium").value(true));

        mockMvc.perform(get("/api/v1/entitlements/features/ai-summary")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(get("/api/v1/admin/premium-special")
                        .with(httpBasic("test-admin", "test-admin-password-12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(user.getId().toString()));

        mockMvc.perform(delete("/api/v1/admin/users/{userId}/premium-special", user.getId())
                        .with(httpBasic("test-admin", "test-admin-password-12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/subscriptions/current")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPlan().getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void rejectsAlreadyExpiredGrant() throws Exception {
        UserEntity user = createUser();
        String expired = Instant.now().minusSeconds(60).toString();

        mockMvc.perform(put("/api/v1/admin/users/{userId}/premium-special", user.getId())
                        .with(httpBasic("test-admin", "test-admin-password-12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"" + expired + "\",\"reason\":\"Expired test\"}"))
                .andExpect(status().isBadRequest());
    }

    private UserEntity createUser() {
        UserEntity user = new UserEntity();
        user.setEmail("special@example.com");
        user.setDisplayName("Special User");
        user.setProvider("GOOGLE");
        user.setProviderUserId("special-google-user");
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        return userRepository.saveAndFlush(user);
    }
}
