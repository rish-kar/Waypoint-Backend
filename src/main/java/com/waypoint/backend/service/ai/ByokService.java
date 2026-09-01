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
    private final OpenAiClient openAiClient;

    public ByokService(
            UserRepository userRepository,
            SubscriptionService subscriptionService,
            ByokApiKeyCipher apiKeyCipher,
            OpenAiClient openAiClient
    ) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.apiKeyCipher = apiKeyCipher;
        this.openAiClient = openAiClient;
    }

    public ByokStatusResponse status(UUID userId) {
        UserEntity user = requireUser(userId);
        boolean eligible = eligible(userId);
        boolean configured = StringUtils.hasText(user.getOpenAiApiKeyCiphertext());
        String selectedModel = cleanModel(user.getOpenAiModel());
        return new ByokStatusResponse(
                eligible,
                configured,
                eligible && configured && StringUtils.hasText(selectedModel),
                selectedModel
        );
    }

    public ByokModelCatalogResponse saveApiKey(UUID userId, String apiKey) {
        requireEligible(userId);
        String normalizedKey = normalizeApiKey(apiKey);
        List<String> models = openAiClient.availableModels(normalizedKey);
        if (models.isEmpty()) {
            throw new InvalidRequestException("No compatible OpenAI text models are available for this API key");
        }

        UserEntity user = requireUser(userId);
        String selectedModel = cleanModel(user.getOpenAiModel());
        if (!models.contains(selectedModel)) {
            selectedModel = "";
        }
        user.setOpenAiApiKeyCiphertext(apiKeyCipher.encrypt(normalizedKey));
        user.setOpenAiModel(selectedModel.isBlank() ? null : selectedModel);
        userRepository.save(user);
        return new ByokModelCatalogResponse(models, selectedModel);
    }

    public ByokModelCatalogResponse models(UUID userId) {
        requireEligible(userId);
        UserEntity user = requireConfiguredUser(userId);
        List<String> models = openAiClient.availableModels(apiKeyCipher.decrypt(user.getOpenAiApiKeyCiphertext()));
        return new ByokModelCatalogResponse(models, cleanModel(user.getOpenAiModel()));
    }

    public ByokStatusResponse selectModel(UUID userId, String model) {
        requireEligible(userId);
        UserEntity user = requireConfiguredUser(userId);
        String requestedModel = cleanModel(model);
        if (!StringUtils.hasText(requestedModel)) {
            throw new InvalidRequestException("OpenAI model is required");
        }

        String apiKey = apiKeyCipher.decrypt(user.getOpenAiApiKeyCiphertext());
        List<String> models = openAiClient.availableModels(apiKey);
        if (!models.contains(requestedModel)) {
            throw new InvalidRequestException("That OpenAI model is not available for this API key");
        }
        user.setOpenAiModel(requestedModel);
        userRepository.save(user);
        return status(userId);
    }

    public ByokStatusResponse remove(UUID userId) {
        UserEntity user = requireUser(userId);
        user.setOpenAiApiKeyCiphertext(null);
        user.setOpenAiModel(null);
        userRepository.save(user);
        return status(userId);
    }

    public Optional<ByokCredentials> credentialsFor(UUID userId) {
        if (userId == null || !eligible(userId)) {
            return Optional.empty();
        }
        UserEntity user = requireUser(userId);
        if (!StringUtils.hasText(user.getOpenAiApiKeyCiphertext()) || !StringUtils.hasText(user.getOpenAiModel())) {
            return Optional.empty();
        }
        return Optional.of(new ByokCredentials(
                apiKeyCipher.decrypt(user.getOpenAiApiKeyCiphertext()),
                cleanModel(user.getOpenAiModel())
        ));
    }

    private boolean eligible(UUID userId) {
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
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
        if (!StringUtils.hasText(user.getOpenAiApiKeyCiphertext())) {
            throw new InvalidRequestException("Add an OpenAI API key first");
        }
        return user;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalizeApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.length() < 20 || normalized.length() > 512 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidRequestException("Enter a valid OpenAI API key");
        }
        return normalized;
    }

    private String cleanModel(String model) {
        return model == null ? "" : model.trim();
    }

    public record ByokCredentials(String apiKey, String model) {
    }
}
