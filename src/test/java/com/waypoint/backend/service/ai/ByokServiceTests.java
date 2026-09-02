package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.ByokModelCatalogResponse;
import com.waypoint.backend.model.ai.ByokStatusResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.ai.ByokApiKeyCipher;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.ai.OpenAiClient;
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
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ByokApiKeyCipher apiKeyCipher;
    @Mock
    private OpenAiClient openAiClient;

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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        service = new ByokService(userRepository, subscriptionService, apiKeyCipher, openAiClient);
    }

    @Test
    void activeMonthlyUserCanSaveKeyAndDiscoverModels() {
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));
        when(openAiClient.availableModels("sk-user-key")).thenReturn(List.of("gpt-4o", "gpt-5.6-sol"));
        when(apiKeyCipher.encrypt("sk-user-key")).thenReturn("v1:ciphertext");

        ByokModelCatalogResponse response = service.saveApiKey(userId, "sk-user-key");

        assertThat(response.models()).containsExactly("gpt-4o", "gpt-5.6-sol");
        assertThat(response.selectedModel()).isEmpty();
        assertThat(user.getOpenAiApiKeyCiphertext()).isEqualTo("v1:ciphertext");
        assertThat(user.getOpenAiModel()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void activeAnnualUserCanSelectAndUseOwnModel() {
        user.setOpenAiApiKeyCiphertext("v1:ciphertext");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_ANNUAL, SubscriptionStatus.ACTIVE, true));
        when(apiKeyCipher.decrypt("v1:ciphertext")).thenReturn("sk-user-key");
        when(openAiClient.availableModels("sk-user-key")).thenReturn(List.of("gpt-5.6-sol", "gpt-5.6-terra"));

        ByokStatusResponse status = service.selectModel(userId, "gpt-5.6-terra");

        assertThat(status.eligible()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.active()).isTrue();
        assertThat(status.selectedModel()).isEqualTo("gpt-5.6-terra");
        assertThat(service.credentialsFor(userId)).hasValueSatisfying(credentials -> {
            assertThat(credentials.apiKey()).isEqualTo("sk-user-key");
            assertThat(credentials.model()).isEqualTo("gpt-5.6-terra");
        });
    }

    @Test
    void trialAndSpecialAccessDoNotQualifyForByok() {
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ON_TRIAL, true));

        assertThatThrownBy(() -> service.saveApiKey(userId, "sk-user-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("BYOK_PREMIUM_REQUIRED"));

        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_SPECIAL, SubscriptionStatus.PREMIUM_SPECIAL, true));

        assertThatThrownBy(() -> service.saveApiKey(userId, "sk-user-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("BYOK_PREMIUM_REQUIRED"));
    }

    @Test
    void removingKeyClearsCredentialsAndSelectedModel() {
        user.setOpenAiApiKeyCiphertext("v1:ciphertext");
        user.setOpenAiModel("gpt-5.6-sol");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, true));

        ByokStatusResponse status = service.remove(userId);

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
