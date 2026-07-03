package com.waypoint.backend.webhook;

import com.waypoint.backend.billing.LemonSqueezyProperties;
import com.waypoint.backend.common.InvalidRequestException;
import com.waypoint.backend.common.UnauthorizedException;
import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
import com.waypoint.backend.user.UserEntity;
import com.waypoint.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final LemonSqueezyProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public WebhookService(
            LemonSqueezyProperties properties,
            ObjectMapper objectMapper,
            WebhookEventRepository webhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webhookEventRepository = webhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void process(byte[] rawBody, String signature) {
        verifySignature(rawBody, signature);
        String eventHash = sha256Hex(rawBody);
        Optional<WebhookEventEntity> existing = webhookEventRepository.findByEventHash(eventHash);
        if (existing.filter(event -> event.getProcessingStatus() == ProcessingStatus.PROCESSED).isPresent()) {
            return;
        }

        String payloadJson = new String(rawBody, StandardCharsets.UTF_8);
        WebhookEventEntity event = existing.orElseGet(WebhookEventEntity::new);
        event.setEventHash(eventHash);
        event.setPayloadJson(payloadJson);
        event.setProcessingStatus(ProcessingStatus.RECEIVED);
        event.setErrorMessage(null);
        event.setReceivedAt(event.getReceivedAt() == null ? Instant.now() : event.getReceivedAt());

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String eventName = text(payload.path("meta"), "event_name");
            String externalObjectId = text(payload.path("data"), "id");
            event.setEventName(StringUtils.hasText(eventName) ? eventName : "UNKNOWN");
            event.setExternalObjectId(externalObjectId);
            webhookEventRepository.save(event);

            UUID userId = parseWaypointUserId(payload);
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidRequestException("Webhook references an unknown Waypoint user"));
            upsertSubscription(payload, eventName, user);

            event.setProcessingStatus(ProcessingStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
        } catch (Exception exception) {
            event.setProcessingStatus(ProcessingStatus.FAILED);
            event.setErrorMessage(safeMessage(exception));
            event.setProcessedAt(Instant.now());
            if (!StringUtils.hasText(event.getEventName())) {
                event.setEventName("UNKNOWN");
            }
            webhookEventRepository.save(event);
            if (exception instanceof InvalidRequestException invalidRequestException) {
                throw invalidRequestException;
            }
            throw new InvalidRequestException("Unable to process webhook payload");
        }
    }

    private void upsertSubscription(JsonNode payload, String eventName, UserEntity user) {
        JsonNode data = payload.path("data");
        JsonNode attributes = data.path("attributes");
        String externalSubscriptionId = subscriptionId(data, attributes);
        if (!StringUtils.hasText(externalSubscriptionId)) {
            externalSubscriptionId = data.path("id").asText(null);
        }
        if (!StringUtils.hasText(externalSubscriptionId)) {
            throw new InvalidRequestException("Webhook payload is missing a subscription ID");
        }

        SubscriptionEntity subscription = subscriptionRepository.findByExternalSubscriptionId(externalSubscriptionId)
                .orElseGet(SubscriptionEntity::new);
        subscription.setUser(user);
        subscription.setProvider("LEMON_SQUEEZY");
        subscription.setExternalSubscriptionId(externalSubscriptionId);
        subscription.setExternalCustomerId(firstText(attributes, "customer_id", relationshipId(data, "customer")));
        subscription.setExternalProductId(firstText(attributes, "product_id", relationshipId(data, "product")));
        subscription.setExternalVariantId(firstText(attributes, "variant_id", relationshipId(data, "variant")));
        subscription.setPlan(resolvePlan(payload, subscription.getExternalVariantId()));
        subscription.setStatus(resolveStatus(eventName, attributes));
        subscription.setRenewsAt(parseInstant(text(attributes, "renews_at")));
        subscription.setEndsAt(parseInstant(text(attributes, "ends_at")));
        subscriptionRepository.save(subscription);
    }

    private SubscriptionStatus resolveStatus(String eventName, JsonNode attributes) {
        if (eventName != null && eventName.toLowerCase().contains("refund")) {
            return SubscriptionStatus.REFUNDED;
        }
        return SubscriptionStatus.fromExternal(text(attributes, "status"));
    }

    private String resolvePlan(JsonNode payload, String variantId) {
        String customPlan = text(payload.path("meta").path("custom_data"), "waypoint_plan");
        if (isPlan(customPlan)) {
            return customPlan;
        }
        if (StringUtils.hasText(variantId) && variantId.equals(properties.annualVariantId())) {
            return CheckoutPlan.ANNUAL.name();
        }
        if (StringUtils.hasText(variantId) && variantId.equals(properties.monthlyVariantId())) {
            return CheckoutPlan.MONTHLY.name();
        }
        return "PREMIUM";
    }

    private boolean isPlan(String value) {
        try {
            CheckoutPlan.valueOf(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private UUID parseWaypointUserId(JsonNode payload) {
        String value = text(payload.path("meta").path("custom_data"), "waypoint_user_id");
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException("Webhook payload is missing waypoint_user_id");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Webhook payload has an invalid waypoint_user_id");
        }
    }

    private String subscriptionId(JsonNode data, JsonNode attributes) {
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

    private void verifySignature(byte[] rawBody, String signature) {
        if (!StringUtils.hasText(properties.webhookSecret())) {
            throw new UnauthorizedException("Webhook signing secret is not configured");
        }
        if (!StringUtils.hasText(signature)) {
            throw new UnauthorizedException("Missing webhook signature");
        }
        String expected = hmacHex(rawBody, properties.webhookSecret());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
    }

    private String hmacHex(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify webhook signature");
        }
    }

    private String sha256Hex(byte[] rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash webhook body");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }
}
