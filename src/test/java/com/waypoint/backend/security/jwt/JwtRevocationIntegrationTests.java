package com.waypoint.backend.security.jwt;

import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;

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

    @Autowired
    JwtRevocationIntegrationTests(
            MockMvc mockMvc,
            JwtService jwtService,
            RevokedJwtTokenRepository revokedJwtTokenRepository
    ) {
        this.mockMvc = mockMvc;
        this.jwtService = jwtService;
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
    }

    @BeforeEach
    void cleanRevokedTokens() {
        revokedJwtTokenRepository.deleteAll();
    }

    @Test
    void logoutRevokesTokenAndFutureRequestsAreRejected() throws Exception {
        String token = jwtService.issueToken(UUID.randomUUID(), "user@example.com");
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
