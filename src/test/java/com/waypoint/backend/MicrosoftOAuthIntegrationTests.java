package com.waypoint.backend;

import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.auth.MicrosoftExchangeCodeRepository;
import com.waypoint.backend.repository.auth.MicrosoftOAuthTransactionRepository;
import com.waypoint.backend.repository.auth.MicrosoftProviderCredentialRepository;
import com.waypoint.backend.repository.auth.RevokedJwtTokenRepository;
import com.waypoint.backend.repository.auth.WaypointRefreshSessionRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.jwt.JwtService;
import com.waypoint.backend.security.oauth.MicrosoftTokenCipher;
import com.waypoint.backend.service.auth.MicrosoftAccountService;
import com.waypoint.backend.service.auth.MicrosoftCredentialService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.client.microsoft.MicrosoftOAuthClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MicrosoftOAuthIntegrationTests.TestConfig.class)
class MicrosoftOAuthIntegrationTests {
    private static final String REDIRECT_URI = "https://test-extension.chromiumapp.org/microsoft";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private MicrosoftOAuthTransactionRepository transactionRepository;
    @Autowired private MicrosoftExchangeCodeRepository exchangeCodeRepository;
    @Autowired private MicrosoftProviderCredentialRepository credentialRepository;
    @Autowired private WaypointRefreshSessionRepository refreshSessionRepository;
    @Autowired private RevokedJwtTokenRepository revokedJwtTokenRepository;
    @Autowired private PlanService planService;
    @Autowired private JwtService jwtService;
    @Autowired private MicrosoftTokenCipher tokenCipher;
    @Autowired private MicrosoftCredentialService credentialService;
    @Autowired private MicrosoftAccountService accountService;
    @Autowired private FakeMicrosoftOAuthClient microsoftClient;

    @BeforeEach
    void cleanDatabase() {
        refreshSessionRepository.deleteAll();
        exchangeCodeRepository.deleteAll();
        transactionRepository.deleteAll();
        credentialRepository.deleteAll();
        revokedJwtTokenRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        microsoftClient.reset();
    }

    @Test
    void completesLoginAndRejectsStateAndExchangeCodeReplay() throws Exception {
        String authorizationUrl = startMicrosoft(false, null);
        assertThat(authorizationUrl).contains("offline_access").contains("User.Read").contains("code_challenge=");
        assertThat(authorizationUrl).doesNotContain("openid").doesNotContain("profile").doesNotContain("email");
        String state = query(authorizationUrl, "state");

        MvcResult callback = callback("code-1", state);
        String exchangeCode = query(callback.getResponse().getHeader("Location"), "exchange_code");
        assertThat(exchangeCode).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/microsoft/callback").param("code", "code-1").param("state", state))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/session/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2592000));

        mockMvc.perform(post("/api/v1/auth/session/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredOAuthState() throws Exception {
        String authorizationUrl = startMicrosoft(false, null);
        String state = query(authorizationUrl, "state");
        var transaction = transactionRepository.findAll().getFirst();
        transaction.setExpiresAt(Instant.now().minusSeconds(1));
        transactionRepository.saveAndFlush(transaction);

        mockMvc.perform(get("/api/v1/auth/microsoft/callback")
                        .param("code", "expired-state-code")
                        .param("state", state))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findAll()).isEmpty();
        assertThat(credentialRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsExpiredExchangeCode() throws Exception {
        String state = query(startMicrosoft(false, null), "state");
        MvcResult callback = callback("expired-exchange-code", state);
        String exchangeCode = query(callback.getResponse().getHeader("Location"), "exchange_code");
        var storedCode = exchangeCodeRepository.findAll().getFirst();
        storedCode.setExpiresAt(Instant.now().minusSeconds(1));
        exchangeCodeRepository.saveAndFlush(storedCode);

        mockMvc.perform(post("/api/v1/auth/session/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesWaypointRefreshSessionWithoutRequiringAnAccessToken() throws Exception {
        LoginResult login = loginMicrosoft();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payload = objectMapper.readTree(refresh.getResponse().getContentAsString());
        String newAccess = payload.get("accessToken").asText();
        String newRefresh = payload.get("refreshToken").asText();
        assertThat(newAccess).isNotEqualTo(login.accessToken());
        assertThat(newRefresh).isNotEqualTo(login.refreshToken());

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesWaypointSessionButPreservesMicrosoftCredential() throws Exception {
        LoginResult login = loginMicrosoft();
        UUID userId = userRepository.findByEmailAndProvider("microsoft@example.com", "MICROSOFT").orElseThrow().getId();
        assertThat(credentialRepository.findByUserId(userId)).isPresent();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(credentialRepository.findByUserId(userId)).isPresent();
        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/session/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void explicitDisconnectDeletesMicrosoftCredential() throws Exception {
        LoginResult login = loginMicrosoft();
        UUID userId = userRepository.findByEmailAndProvider("microsoft@example.com", "MICROSOFT").orElseThrow().getId();
        assertThat(credentialRepository.findByUserId(userId)).isPresent();

        mockMvc.perform(delete("/api/v1/auth/microsoft").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(credentialRepository.findByUserId(userId)).isEmpty();
        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void createsSeparateMicrosoftAccountWhenGoogleUsesSameEmail() throws Exception {
        UserEntity googleUser = createGoogleUser("microsoft@example.com");
        String state = query(startMicrosoft(false, null), "state");
        MvcResult callback = callback("code-2", state);
        String location = callback.getResponse().getHeader("Location");

        assertThat(location).contains("waypoint_auth=success").contains("exchange_code=");
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userRepository.findByEmailAndProvider("microsoft@example.com", "GOOGLE")
                .map(UserEntity::getId)).contains(googleUser.getId());

        UserEntity microsoftUser = userRepository
                .findByEmailAndProvider("microsoft@example.com", "MICROSOFT")
                .orElseThrow();
        assertThat(microsoftUser.getId()).isNotEqualTo(googleUser.getId());
        assertThat(credentialRepository.findByUserId(microsoftUser.getId())).isPresent();
    }

    @Test
    void authenticatedLinkPreservesCanonicalGoogleIdentity() throws Exception {
        UserEntity googleUser = createGoogleUser("google@example.com");
        microsoftClient.profile = new MicrosoftProfile("ms-linked", "different-microsoft@example.com", "Microsoft Name");
        String bearer = jwtService.issueToken(googleUser.getId(), googleUser.getEmail());
        String state = query(startMicrosoft(true, bearer), "state");

        MvcResult callback = callback("code-link", state);
        assertThat(callback.getResponse().getHeader("Location")).contains("waypoint_auth=success");

        UserEntity after = userRepository.findById(googleUser.getId()).orElseThrow();
        assertThat(after.getProvider()).isEqualTo("GOOGLE");
        assertThat(after.getEmail()).isEqualTo("google@example.com");
        assertThat(after.getDisplayName()).isEqualTo("Google User");
        assertThat(credentialRepository.findByUserId(googleUser.getId())).isPresent();
    }

    @Test
    void logoutPreservesExplicitMicrosoftLink() throws Exception {
        UserEntity googleUser = createGoogleUser("google@example.com");
        microsoftClient.profile = new MicrosoftProfile("ms-linked", "different-microsoft@example.com", "Microsoft Name");
        String bearer = jwtService.issueToken(googleUser.getId(), googleUser.getEmail());
        String state = query(startMicrosoft(true, bearer), "state");
        callback("code-link", state);

        assertThat(credentialRepository.findByUserId(googleUser.getId())).isPresent();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isNoContent());

        var credential = credentialRepository.findByProviderUserId("ms-linked").orElseThrow();
        assertThat(credential.getUser().getId()).isEqualTo(googleUser.getId());
    }

    @Test
    void rotatesMicrosoftRefreshTokenAndDeletesRejectedCredential() throws Exception {
        loginMicrosoft();
        UUID userId = userRepository.findByEmailAndProvider("microsoft@example.com", "MICROSOFT").orElseThrow().getId();

        MicrosoftTokenSet rotated = credentialService.refreshAccessToken(userId);
        assertThat(rotated.refreshToken()).startsWith("provider-refresh-rotated-");
        String stored = tokenCipher.decrypt(credentialRepository.findByUserId(userId).orElseThrow().getRefreshTokenCiphertext());
        assertThat(stored).isEqualTo(rotated.refreshToken());

        microsoftClient.rejectRefresh = true;
        assertThatThrownBy(() -> credentialService.refreshAccessToken(userId))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(credentialRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void providerFailureIsSanitizedAndCreatesNoCredential() throws Exception {
        microsoftClient.rejectExchange = true;
        String state = query(startMicrosoft(false, null), "state");
        MvcResult callback = callback("provider-fail", state);

        assertThat(callback.getResponse().getHeader("Location"))
                .contains("waypoint_auth=error").contains("authentication_failed");
        assertThat(userRepository.findAll()).isEmpty();
        assertThat(credentialRepository.findAll()).isEmpty();
    }

    @Test
    void concurrentFirstLoginCreatesSingleUserAndCredential() throws Exception {
        MicrosoftProfile profile = new MicrosoftProfile("ms-race", "race@example.com", "Race User");
        MicrosoftTokenSet tokens = new MicrosoftTokenSet("access", "refresh", 3600, "offline_access User.Read");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<UserEntity>> results = executor.invokeAll(List.of(
                    () -> accountService.link(profile, tokens, null),
                    () -> accountService.link(profile, tokens, null)
            ));
            UUID first = results.get(0).get().getId();
            UUID second = results.get(1).get().getId();
            assertThat(first).isEqualTo(second);
            assertThat(userRepository.findAll()).hasSize(1);
            assertThat(credentialRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private LoginResult loginMicrosoft() throws Exception {
        String state = query(startMicrosoft(false, null), "state");
        MvcResult callback = callback("code-login", state);
        String exchangeCode = query(callback.getResponse().getHeader("Location"), "exchange_code");
        MvcResult exchange = mockMvc.perform(post("/api/v1/auth/session/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payload = objectMapper.readTree(exchange.getResponse().getContentAsString());
        return new LoginResult(payload.get("accessToken").asText(), payload.get("refreshToken").asText());
    }

    private String startMicrosoft(boolean link, String bearer) throws Exception {
        var request = post(link ? "/api/v1/auth/microsoft/link/start" : "/api/v1/auth/microsoft/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redirectUri\":\"" + REDIRECT_URI + "\"}");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("authorizationUrl").asText();
    }

    private MvcResult callback(String code, String state) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/microsoft/callback").param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn();
    }

    private String query(String uri, String name) {
        return UriComponentsBuilder.fromUriString(uri).build().getQueryParams().getFirst(name);
    }

    private UserEntity createGoogleUser(String email) {
        UserEntity user = new UserEntity();
        user.setProvider("GOOGLE");
        user.setProviderUserId("google-" + UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName("Google User");
        user.setPlan(planService.require(PlanCode.FREE));
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private record LoginResult(String accessToken, String refreshToken) { }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeMicrosoftOAuthClient fakeMicrosoftOAuthClient() {
            return new FakeMicrosoftOAuthClient();
        }
    }

    static class FakeMicrosoftOAuthClient implements MicrosoftOAuthClient {
        private MicrosoftProfile profile;
        private boolean rejectExchange;
        private boolean rejectRefresh;
        private int refreshCounter;

        void reset() {
            profile = new MicrosoftProfile("ms-user-123", "microsoft@example.com", "Microsoft User");
            rejectExchange = false;
            rejectRefresh = false;
            refreshCounter = 0;
        }

        @Override
        public MicrosoftTokenSet exchangeAuthorizationCode(String authorizationCode, String codeVerifier) {
            if (rejectExchange) throw new UnauthorizedException("provider details must not escape");
            return new MicrosoftTokenSet("provider-access", "provider-refresh", 3600, "offline_access User.Read");
        }

        @Override
        public MicrosoftTokenSet refreshAccessToken(String refreshToken) {
            if (rejectRefresh) throw new UnauthorizedException("provider refresh rejected");
            refreshCounter++;
            return new MicrosoftTokenSet(
                    "provider-access-rotated-" + refreshCounter,
                    "provider-refresh-rotated-" + refreshCounter,
                    3600,
                    "offline_access User.Read"
            );
        }

        @Override
        public MicrosoftProfile fetchProfile(String accessToken) {
            return profile;
        }
    }
}
