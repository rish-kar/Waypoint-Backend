package com.waypoint.backend.webhook;

import com.waypoint.backend.common.InvalidRequestException;
import com.waypoint.backend.subscription.SubscriptionAccessPolicy;
import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
import com.waypoint.backend.user.UserEntity;
import com.waypoint.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookSubscriptionProcessor {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;

    public WebhookSubscriptionProcessor(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
    }

    @Transactional
    public void process(JsonNode payload, String eventName) {
        JsonNode data = payload.path("data");
        JsonNode attributes = data.path("attributes");
        String externalSubscriptionId = requireSubscriptionId(data, attributes);

        Optional<SubscriptionEntity> existing = subscriptionRepository.findByExternalSubscriptionId(externalSubscriptionId);
        Optional<UUID> customUserId = optionalWaypointUserId(payload);
        UserEntity user = resolveUser(existing, customUserId);

        SubscriptionEntity subscription = existing.orElseGet(SubscriptionEntity::new);
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId(externalSubscriptionId);
        subscription.setExternalCustomerId(firstText(attributes, "customer_id", relationshipId(data, "customer")));
        subscription.setExternalProductId(firstText(attributes, "product_id", relationshipId(data, "product")));
        subscription.setExternalVariantId(firstText(attributes, "variant_id", relationshipId(data, "variant")));
        subscription.setPlan(subscriptionAccessPolicy.planForVariant(subscription.getExternalVariantId()));
        subscription.setStatus(resolveStatus(eventName, attributes));
        subscription.setRenewsAt(parseInstant(text(attributes, "renews_at")));
        subscription.setEndsAt(parseInstant(text(attributes, "ends_at")));
        subscriptionRepository.save(subscription);
    }

    public String subscriptionId(JsonNode data, JsonNode attributes) {
        String id = text(attributes, "subscription_id");
        if (StringUtils.hasText(id)) {
            return id;
        }
        id = relationshipId(data, "subscription");
        if (StringUtils.hasText(id)) {
            return id;
        }
        return data.path("type").asText("").contains("subscription") ? text(data, "id") : null;
    }

    private UserEntity resolveUser(Optional<SubscriptionEntity> existing, Optional<UUID> customUserId) {
        if (existing.isPresent()) {
            UserEntity existingUser = existing.get().getUser();
            if (customUserId.isPresent() && !existingUser.getId().equals(customUserId.get())) {
                throw new InvalidRequestException("Webhook custom user does not match existing subscription owner");
            }
            return existingUser;
        }
        UUID userId = customUserId.orElseThrow(() -> new InvalidRequestException("Webhook payload is missing waypoint_user_id"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("Webhook references an unknown Waypoint user"));
    }

    private Optional<UUID> optionalWaypointUserId(JsonNode payload) {
        String value = text(payload.path("meta").path("custom_data"), "waypoint_user_id");
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Webhook payload has an invalid waypoint_user_id");
        }
    }

    private String requireSubscriptionId(JsonNode data, JsonNode attributes) {
        String externalSubscriptionId = subscriptionId(data, attributes);
        if (!StringUtils.hasText(externalSubscriptionId)) {
            throw new InvalidRequestException("Webhook payload is missing a subscription ID");
        }
        return externalSubscriptionId;
    }

    private SubscriptionStatus resolveStatus(String eventName, JsonNode attributes) {
        if (eventName != null && eventName.toLowerCase().contains("refund")) {
            return SubscriptionStatus.REFUNDED;
        }
        return SubscriptionStatus.fromExternal(text(attributes, "status"));
    }

    private String relationshipId(JsonNode data, String relationship) {
        return text(data.path("relationships").path(relationship).path("data"), "id");
    }

    private String firstText(JsonNode attributes, String field, String fallback) {
        String value = text(attributes, field);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value);
            } catch (Exception exception) {
                return null;
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
