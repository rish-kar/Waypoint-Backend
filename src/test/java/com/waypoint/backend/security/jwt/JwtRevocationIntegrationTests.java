package com.waypoint.backend.security.jwt;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtRevocationIntegrationTests {
    private final MockMvc mockMvc;
    private final JwtService jwtService;
    private final RevokedJwtTokenRepository revokedJwtTokenRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    @Autowired
    JwtRevocationIntegrationTests(
            MockMvc mockMvc,
            JwtService jwtService,
            RevokedJwtTokenRepository revokedJwtTokenRepository,
            UserRepository userRepository,
            PlanRepository planRepository
    ) {
        this.mockMvc = mockMvc;
        this.jwtService = jwtService;
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    @BeforeEach
    void cleanRevokedTokens() {
        revokedJwtTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void logoutRevokesTokenAndFutureRequestsAreRejected() throws Exception {
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setDisplayName("JWT Revocation User");
        user.setProvider("GOOGLE");
        user.setProviderUserId("jwt-revocation-user");
        user.setPlan(planRepository.findById(PlanCode.FREE).orElseThrow());
        user = userRepository.saveAndFlush(user);

        String token = jwtService.issueToken(user.getId(), user.getEmail());
        JwtClaims claims = jwtService.parseToken(token);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(revokedJwtTokenRepository.existsById(claims.tokenId())).isTrue();

        mockMvc.perform(get("/api/v1/billing/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
