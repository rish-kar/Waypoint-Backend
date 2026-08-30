package com.waypoint.backend;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.WaypointRefreshSessionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.utilities.client.google.GoogleProfileClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:waypoint-session;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret=test-secret-that-is-long-enough-for-hmac",
        "jwt.expiration-seconds=1",
        "waypoint-session.refresh-token-ttl-seconds=2592000",
        "waypoint-session.cleanup-ms=3600000",
        "google.client-id=test-google-client",
        "lemon-squeezy.api-key=test-api-key",
        "lemon-squeezy.store-id=123",
        "lemon-squeezy.monthly-variant-id=111",
        "lemon-squeezy.annual-variant-id=222",
        "lemon-squeezy.webhook-secret=test-webhook-secret",
        "cors.allowed-origins=http://localhost:5173",
        "app.base-url=http://localhost:8080"
})
@AutoConfigureMockMvc
class WaypointSessionIntegrationTests {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final WaypointRefreshSessionRepository refreshSessionRepository;
    private final FakeGoogleProfileClient googleProfileClient;

    @Autowired
    WaypointSessionIntegrationTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            WaypointRefreshSessionRepository refreshSessionRepository,
            FakeGoogleProfileClient googleProfileClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.googleProfileClient = googleProfileClient;
    }

    @BeforeEach
    void resetState() {
        refreshSessionRepository.deleteAll();
        userRepository.deleteAll();
        googleProfileClient.profile = new GoogleProfile(
                "google-refresh-user",
                "refresh@example.com",
                true,
                "Refresh User",
                null,
                "test-google-client",
                300
        );
        googleProfileClient.fail = false;
    }

    @Test
    void refreshesExpiredAccessTokenThenPersistsPhoneAndRevokesSessionOnLogout() throws Exception {
        JsonNode login = login();
        String expiredAccessToken = login.get("accessToken").asText();
        String firstRefreshToken = login.get("refreshToken").asText();
        assertThat(refreshSessionRepository.findAll()).hasSize(1);

        Thread.sleep(1200L);

        mockMvc.perform(get("/api/v1/account")
                        .header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized());

        JsonNode firstRefresh = refresh(firstRefreshToken);
        String refreshedAccessToken = firstRefresh.get("accessToken").asText();
        String secondRefreshToken = firstRefresh.get("refreshToken").asText();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        mockMvc.perform(patch("/api/v1/account")
                        .header("Authorization", "Bearer " + refreshedAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+91 9916604905\",\"phoneCountryCode\":\"IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+91 9916604905"))
                .andExpect(jsonPath("$.phoneCountryCode").value("IN"));

        UserEntity savedUser = userRepository.findAll().getFirst();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("+91 9916604905");
        assertThat(savedUser.getPhoneCountryCode()).isEqualTo("IN");

        mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(firstRefreshToken))))
                .andExpect(status().isUnauthorized());

        JsonNode secondRefresh = refresh(secondRefreshToken);
        String logoutAccessToken = secondRefresh.get("accessToken").asText();
        String logoutRefreshToken = secondRefresh.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + logoutAccessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(logoutRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"valid-google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshExpiresIn").value(2592000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private record RefreshRequest(String refreshToken) {
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        FakeGoogleProfileClient fakeGoogleProfileClient() {
            return new FakeGoogleProfileClient();
        }
    }

    static class FakeGoogleProfileClient implements GoogleProfileClient {
        GoogleProfile profile;
        boolean fail;

        @Override
        public GoogleProfile fetchProfile(String accessToken) {
            if (fail) throw new UnauthorizedException("Invalid Google access token");
            return profile;
        }
    }
}
