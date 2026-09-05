package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.ByokModelCatalogResponse;
import com.waypoint.backend.model.ai.ByokProvider;
import com.waypoint.backend.model.ai.ByokProviderResponse;
import com.waypoint.backend.model.ai.ByokStatusResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.ai.ByokApiKeyCipher;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ByokServiceTests {
    private static final String USER_API_KEY = "sk-project-test-12345678901234567890";
    private static final List<ByokProviderResponse> PROVIDERS = List.of(
            new ByokProviderResponse("openai", "OpenAI"),
            new ByokProviderResponse("anthropic", "Anthropic Claude"),
            new ByokProviderResponse("google", "Google Gemini"),
            new ByokProviderResponse("xai", "xAI Grok"),
            new ByokProviderResponse("openrouter", "OpenRouter")
    );

    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ByokApiKeyCipher apiKeyCipher;
    @Mock
    private ByokProviderRegistry providerRegistry;

    private UUID userId;
    private UserEntity user;
    private ByokService service;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setProvider("GOOGLE");
        user.setProviderUserId("provider-user");
        service = new ByokService(userRepository, subscriptionService, apiKeyCipher, providerRegistry);
        when(providerRegistry.providers()).thenReturn(PROVIDERS);
    }

    @Test
    void activeMonthlyUserCanSaveProviderKeyAndDiscoverModels() {
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(providerRegistry.availableModels(ByokProvider.OPENAI, USER_API_KEY)).thenReturn(List.of("gpt-4o", "gpt-5.6-sol"));
        when(apiKeyCipher.encrypt(USER_API_KEY)).thenReturn("v1:ciphertext");

        ByokModelCatalogResponse response = service.saveApiKey(userId, "openai", USER_API_KEY);

        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.models()).containsExactly("gpt-4o", "gpt-5.6-sol");
        assertThat(response.selectedModel()).isEmpty();
        assertThat(user.getByokProvider()).isEqualTo("openai");
        assertThat(user.getByokApiKeyCiphertext()).isEqualTo("v1:ciphertext");
        assertThat(user.getByokModel()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void activeAnnualUserCanSelectAndUseOwnProviderModel() {
        user.setByokProvider("google");
        user.setByokApiKeyCiphertext("v1:ciphertext");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_ANNUAL, SubscriptionStatus.ACTIVE, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(apiKeyCipher.decrypt("v1:ciphertext")).thenReturn(USER_API_KEY);
        when(providerRegistry.availableModels(ByokProvider.GOOGLE, USER_API_KEY))
                .thenReturn(List.of("gemini-3.8-flash", "gemini-3.8-pro"));

        ByokStatusResponse status = service.selectModel(userId, "gemini-3.8-pro");

        assertThat(status.eligible()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.active()).isTrue();
        assertThat(status.provider()).isEqualTo("google");
        assertThat(status.selectedModel()).isEqualTo("gemini-3.8-pro");
        assertThat(service.credentialsFor(userId)).hasValueSatisfying(credentials -> {
            assertThat(credentials.provider()).isEqualTo(ByokProvider.GOOGLE);
            assertThat(credentials.apiKey()).isEqualTo(USER_API_KEY);
            assertThat(credentials.model()).isEqualTo("gemini-3.8-pro");
        });
    }

    @Test
    void changingProviderClearsModelFromPreviousProvider() {
        user.setByokProvider("openai");
        user.setByokApiKeyCiphertext("old-ciphertext");
        user.setByokModel("gpt-5.6-sol");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(providerRegistry.availableModels(ByokProvider.ANTHROPIC, USER_API_KEY))
                .thenReturn(List.of("claude-sonnet-4-6"));
        when(apiKeyCipher.encrypt(USER_API_KEY)).thenReturn("new-ciphertext");

        ByokModelCatalogResponse response = service.saveApiKey(userId, "anthropic", USER_API_KEY);

        assertThat(response.provider()).isEqualTo("anthropic");
        assertThat(response.selectedModel()).isEmpty();
        assertThat(user.getByokProvider()).isEqualTo("anthropic");
        assertThat(user.getByokModel()).isNull();
    }

    @Test
    void trialAndSpecialAccessDoNotQualifyForByok() {
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ON_TRIAL, true));

        assertThatThrownBy(() -> service.saveApiKey(userId, "openai", USER_API_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("BYOK_PREMIUM_REQUIRED"));

        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_SPECIAL, SubscriptionStatus.PREMIUM_SPECIAL, true));

        assertThatThrownBy(() -> service.saveApiKey(userId, "openai", USER_API_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("BYOK_PREMIUM_REQUIRED"));
    }

    @Test
    void legacyOpenAiSettingsRemainReadableDuringMigration() {
        user.setOpenAiApiKeyCiphertext("v1:legacy");
        user.setOpenAiModel("gpt-4o");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(apiKeyCipher.decrypt("v1:legacy")).thenReturn(USER_API_KEY);

        assertThat(service.credentialsFor(userId)).hasValueSatisfying(credentials -> {
            assertThat(credentials.provider()).isEqualTo(ByokProvider.OPENAI);
            assertThat(credentials.model()).isEqualTo("gpt-4o");
        });
    }

    @Test
    void removingKeyClearsProviderCredentialsAndSelectedModel() {
        user.setByokProvider("xai");
        user.setByokApiKeyCiphertext("v1:ciphertext");
        user.setByokModel("grok-4.6");
        user.setOpenAiApiKeyCiphertext("v1:legacy");
        user.setOpenAiModel("gpt-4o");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));

        ByokStatusResponse status = service.remove(userId);

        assertThat(user.getByokProvider()).isNull();
        assertThat(user.getByokApiKeyCiphertext()).isNull();
        assertThat(user.getByokModel()).isNull();
        assertThat(user.getOpenAiApiKeyCiphertext()).isNull();
        assertThat(user.getOpenAiModel()).isNull();
        assertThat(status.configured()).isFalse();
        assertThat(status.active()).isFalse();
        verify(userRepository).save(user);
    }

    private SubscriptionSnapshot snapshot(PlanCode plan, SubscriptionStatus status, boolean premium) {
        Instant now = Instant.now();
        return new SubscriptionSnapshot(plan, status, premium, null, null, null, null, now);
    }
}
