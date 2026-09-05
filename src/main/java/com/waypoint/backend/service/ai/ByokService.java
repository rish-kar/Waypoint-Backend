package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.ByokModelCatalogResponse;
import com.waypoint.backend.model.ai.ByokProvider;
import com.waypoint.backend.model.ai.ByokStatusResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.security.ai.ByokApiKeyCipher;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.ApiException;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ByokService {
    private static final Set<PlanCode> ELIGIBLE_PLANS = Set.of(
            PlanCode.PREMIUM_MONTHLY,
            PlanCode.PREMIUM_ANNUAL
    );

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final ByokApiKeyCipher apiKeyCipher;
    private final ByokProviderRegistry providerRegistry;

    public ByokService(
            UserRepository userRepository,
            SubscriptionService subscriptionService,
            ByokApiKeyCipher apiKeyCipher,
            ByokProviderRegistry providerRegistry
    ) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.apiKeyCipher = apiKeyCipher;
        this.providerRegistry = providerRegistry;
    }

    public ByokStatusResponse status(UUID userId) {
        UserEntity user = requireUser(userId);
        boolean eligible = eligible(userId);
        boolean configured = StringUtils.hasText(ciphertext(user));
        String provider = configured ? providerFor(user).id() : "";
        String selectedModel = configured ? selectedModel(user) : "";
        return new ByokStatusResponse(
                eligible,
                configured,
                eligible && configured && StringUtils.hasText(selectedModel),
                provider,
                selectedModel,
                providerRegistry.providers()
        );
    }

    public ByokModelCatalogResponse saveApiKey(UUID userId, String providerId, String apiKey) {
        requireEligible(userId);
        ByokProvider provider = requireProvider(providerId);
        String normalizedKey = normalizeApiKey(apiKey);
        List<String> models = providerRegistry.availableModels(provider, normalizedKey);
        if (models.isEmpty()) {
            throw new InvalidRequestException("No compatible text models are available for this " + provider.displayName() + " API key");
        }

        UserEntity user = requireUser(userId);
        String selectedModel = "";
        if (StringUtils.hasText(ciphertext(user)) && providerFor(user) == provider) {
            selectedModel = selectedModel(user);
            if (!models.contains(selectedModel)) {
                selectedModel = "";
            }
        }

        user.setByokProvider(provider.id());
        user.setByokApiKeyCiphertext(apiKeyCipher.encrypt(normalizedKey));
        user.setByokModel(selectedModel.isBlank() ? null : selectedModel);
        clearLegacyOpenAiSettings(user);
        userRepository.save(user);
        return new ByokModelCatalogResponse(provider.id(), models, selectedModel);
    }

    public ByokModelCatalogResponse models(UUID userId) {
        requireEligible(userId);
        UserEntity user = requireConfiguredUser(userId);
        ByokProvider provider = providerFor(user);
        List<String> models = providerRegistry.availableModels(provider, apiKeyCipher.decrypt(ciphertext(user)));
        String selectedModel = selectedModel(user);
        if (StringUtils.hasText(selectedModel) && !models.contains(selectedModel)) {
            user.setByokModel(null);
            user.setOpenAiModel(null);
            userRepository.save(user);
            selectedModel = "";
        }
        return new ByokModelCatalogResponse(provider.id(), models, selectedModel);
    }

    public ByokStatusResponse selectModel(UUID userId, String model) {
        requireEligible(userId);
        UserEntity user = requireConfiguredUser(userId);
        ByokProvider provider = providerFor(user);
        String requestedModel = clean(model);
        if (!StringUtils.hasText(requestedModel)) {
            throw new InvalidRequestException("AI model is required");
        }

        String apiKey = apiKeyCipher.decrypt(ciphertext(user));
        List<String> models = providerRegistry.availableModels(provider, apiKey);
        if (!models.contains(requestedModel)) {
            throw new InvalidRequestException("That model is not available for this " + provider.displayName() + " API key");
        }
        user.setByokModel(requestedModel);
        user.setOpenAiModel(null);
        userRepository.save(user);
        return status(userId);
    }

    public ByokStatusResponse remove(UUID userId) {
        UserEntity user = requireUser(userId);
        user.setByokProvider(null);
        user.setByokApiKeyCiphertext(null);
        user.setByokModel(null);
        clearLegacyOpenAiSettings(user);
        userRepository.save(user);
        return status(userId);
    }

    public Optional<ByokCredentials> credentialsFor(UUID userId) {
        if (userId == null || !eligible(userId)) {
            return Optional.empty();
        }
        UserEntity user = requireUser(userId);
        String encryptedKey = ciphertext(user);
        String model = selectedModel(user);
        if (!StringUtils.hasText(encryptedKey) || !StringUtils.hasText(model)) {
            return Optional.empty();
        }
        return Optional.of(new ByokCredentials(
                providerFor(user),
                apiKeyCipher.decrypt(encryptedKey),
                model
        ));
    }

    private boolean eligible(UUID userId) {
        SubscriptionSnapshot subscription = subscriptionService.currentBilling(userId);
        return subscription != null
                && subscription.premium()
                && subscription.status() != SubscriptionStatus.ON_TRIAL
                && ELIGIBLE_PLANS.contains(subscription.planCode());
    }

    private void requireEligible(UUID userId) {
        if (!eligible(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "BYOK_PREMIUM_REQUIRED",
                    "Bring Your Own Key is available only on Premium Monthly or Premium Annual."
            );
        }
    }

    private UserEntity requireConfiguredUser(UUID userId) {
        UserEntity user = requireUser(userId);
        if (!StringUtils.hasText(ciphertext(user))) {
            throw new InvalidRequestException("Add an AI provider API key first");
        }
        providerFor(user);
        return user;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private ByokProvider requireProvider(String providerId) {
        return ByokProvider.find(providerId)
                .orElseThrow(() -> new InvalidRequestException("Choose a supported AI provider"));
    }

    private ByokProvider providerFor(UserEntity user) {
        String providerId = clean(user.getByokProvider());
        if (!StringUtils.hasText(providerId) && StringUtils.hasText(user.getOpenAiApiKeyCiphertext())) {
            return ByokProvider.OPENAI;
        }
        return ByokProvider.find(providerId)
                .orElseThrow(() -> new InvalidRequestException("The saved AI provider is no longer supported"));
    }

    private String ciphertext(UserEntity user) {
        if (StringUtils.hasText(user.getByokApiKeyCiphertext())) {
            return user.getByokApiKeyCiphertext().trim();
        }
        return clean(user.getOpenAiApiKeyCiphertext());
    }

    private String selectedModel(UserEntity user) {
        if (StringUtils.hasText(user.getByokModel())) {
            return user.getByokModel().trim();
        }
        return clean(user.getOpenAiModel());
    }

    private String normalizeApiKey(String apiKey) {
        String normalized = clean(apiKey);
        if (normalized.length() < 20 || normalized.length() > 512 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidRequestException("Enter a valid API key");
        }
        return normalized;
    }

    private void clearLegacyOpenAiSettings(UserEntity user) {
        user.setOpenAiApiKeyCiphertext(null);
        user.setOpenAiModel(null);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record ByokCredentials(ByokProvider provider, String apiKey, String model) {
    }
}
