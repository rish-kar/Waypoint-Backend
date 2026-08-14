package com.waypoint.backend.service.webhook;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.subscription.SubscriptionAccessPolicy;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

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
    private static final String SUBSCRIPTION_INVOICE_REFUND_EVENT = "subscription_payment_refunded";

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final PlanService planService;
    private final LemonSqueezyProperties properties;

    public WebhookSubscriptionProcessor(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            PlanService planService,
            LemonSqueezyProperties properties
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.planService = planService;
        this.properties = properties;
    }

    @Transactional
    public void process(JsonNode payload, String eventName) {
        JsonNode data = payload.path("data");
        JsonNode attributes = data.path("attributes");
        validatePayloadSource(data, attributes, eventName);

        String externalSubscriptionId = requireSubscriptionId(data, attributes);
        Optional<SubscriptionEntity> existing = subscriptionRepository.findByExternalSubscriptionId(externalSubscriptionId);
        Optional<UUID> customUserId = optionalWaypointUserId(payload);
        UserEntity user = resolveUser(existing, customUserId);

        SubscriptionEntity subscription = existing.orElseGet(SubscriptionEntity::new);
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId(externalSubscriptionId);
        setIfPresent(subscription::setExternalCustomerId, firstText(attributes, "customer_id", relationshipId(data, "customer")));
        setIfPresent(subscription::setExternalProductId, firstText(attributes, "product_id", relationshipId(data, "product")));

        String variantId = firstText(attributes, "variant_id", relationshipId(data, "variant"));
        if (StringUtils.hasText(variantId)) {
            subscription.setExternalVariantId(variantId);
            subscription.setPlan(subscriptionAccessPolicy.planForVariant(variantId));
        } else if (!StringUtils.hasText(subscription.getPlan())) {
            subscription.setPlan("UNKNOWN");
        }

        subscription.setStatus(resolveStatus(eventName, attributes));

        if (attributes.get("trial_ends_at") != null) {
            subscription.setTrialEndsAt(parseInstant(text(attributes, "trial_ends_at")));
        }
        if (attributes.get("renews_at") != null) {
            subscription.setRenewsAt(parseInstant(text(attributes, "renews_at")));
        }
        if (attributes.get("ends_at") != null) {
            subscription.setEndsAt(parseInstant(text(attributes, "ends_at")));
        }

        subscriptionRepository.save(subscription);
        planService.synchronizeUserPlan(user);
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

    private void validatePayloadSource(JsonNode data, JsonNode attributes, String eventName) {
        String expectedType = SUBSCRIPTION_INVOICE_REFUND_EVENT.equalsIgnoreCase(eventName)
                ? "subscription-invoices"
                : "subscriptions";
        String dataType = text(data, "type");
        if (!expectedType.equals(dataType)) {
            throw new InvalidRequestException("Webhook payload has an unexpected data type");
        }

        String storeId = text(attributes, "store_id");
        if (StringUtils.hasText(storeId) && !properties.storeId().equals(storeId)) {
            throw new InvalidRequestException("Webhook payload belongs to an unexpected Lemon Squeezy store");
        }
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
        if (SUBSCRIPTION_INVOICE_REFUND_EVENT.equalsIgnoreCase(eventName)) {
            return SubscriptionStatus.REFUNDED;
        }

        SubscriptionStatus status = SubscriptionStatus.fromExternal(text(attributes, "status"));
        if (status != SubscriptionStatus.UNKNOWN) {
            return status;
        }

        return switch (eventName == null ? "" : eventName.toLowerCase()) {
            case "subscription_cancelled" -> SubscriptionStatus.CANCELLED;
            case "subscription_resumed", "subscription_unpaused" -> SubscriptionStatus.ACTIVE;
            case "subscription_expired" -> SubscriptionStatus.EXPIRED;
            case "subscription_paused" -> SubscriptionStatus.PAUSED;
            default -> SubscriptionStatus.UNKNOWN;
        };
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(value);
        }
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
