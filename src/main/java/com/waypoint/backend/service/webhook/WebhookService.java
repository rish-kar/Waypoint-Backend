package com.waypoint.backend.service.webhook;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SHA256_HEX_LENGTH = 64;
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "subscription_created",
            "subscription_updated",
            "subscription_cancelled",
            "subscription_resumed",
            "subscription_expired",
            "subscription_paused",
            "subscription_unpaused",
            "subscription_plan_changed",
            "subscription_payment_refunded"
    );

    private final LemonSqueezyProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookEventStore webhookEventStore;
    private final WebhookSubscriptionProcessor webhookSubscriptionProcessor;

    public WebhookService(
            LemonSqueezyProperties properties,
            ObjectMapper objectMapper,
            WebhookEventStore webhookEventStore,
            WebhookSubscriptionProcessor webhookSubscriptionProcessor
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webhookEventStore = webhookEventStore;
        this.webhookSubscriptionProcessor = webhookSubscriptionProcessor;
    }

    public void process(byte[] rawBody, String signature) {
        if (rawBody == null || rawBody.length == 0) {
            throw new InvalidRequestException("Webhook payload is empty");
        }

        verifySignature(rawBody, signature);
        String eventHash = sha256Hex(rawBody);
        String payloadJson = new String(rawBody, StandardCharsets.UTF_8);
        WebhookEventStore.WebhookReception reception = webhookEventStore.recordReceived(eventHash, payloadJson);
        if (!reception.shouldProcess()) {
            LOGGER.atInfo()
                    .addKeyValue("event", "webhook_duplicate_ignored")
                    .addKeyValue("provider", "lemon_squeezy")
                    .addKeyValue("event_hash", eventHash)
                    .log("Duplicate webhook delivery ignored");
            return;
        }

        String eventName = "UNKNOWN";
        String externalObjectId = null;
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            eventName = normalizeEventName(text(payload.path("meta"), "event_name"));
            externalObjectId = text(payload.path("data"), "id");

            if (!StringUtils.hasText(eventName) || "UNKNOWN".equals(eventName)) {
                throw new InvalidRequestException("Webhook payload is missing meta.event_name");
            }

            if (!SUPPORTED_EVENTS.contains(eventName)) {
                webhookEventStore.markIgnored(eventHash, eventName, externalObjectId);
                LOGGER.atInfo()
                        .addKeyValue("event", "webhook_event_ignored")
                        .addKeyValue("provider", "lemon_squeezy")
                        .addKeyValue("event_name", eventName)
                        .addKeyValue("external_object_id", externalObjectId)
                        .log("Webhook event does not affect local subscription state");
                return;
            }

            webhookSubscriptionProcessor.process(payload, eventName);
            webhookEventStore.markProcessed(eventHash, eventName, externalObjectId);
            LOGGER.atInfo()
                    .addKeyValue("event", "webhook_processed")
                    .addKeyValue("provider", "lemon_squeezy")
                    .addKeyValue("event_name", eventName)
                    .addKeyValue("external_object_id", externalObjectId)
                    .log("Webhook processed");
        } catch (Exception exception) {
            webhookEventStore.markFailed(eventHash, eventName, externalObjectId, safeMessage(exception));
            LOGGER.atWarn()
                    .addKeyValue("event", "webhook_processing_failed")
                    .addKeyValue("provider", "lemon_squeezy")
                    .addKeyValue("event_name", eventName)
                    .addKeyValue("external_object_id", externalObjectId)
                    .addKeyValue("reason", exception.getClass().getSimpleName())
                    .log("Webhook processing failed");
            if (exception instanceof InvalidRequestException invalidRequestException) {
                throw invalidRequestException;
            }
            throw new InvalidRequestException("Unable to process webhook payload");
        }
    }

    private void verifySignature(byte[] rawBody, String signature) {
        if (!StringUtils.hasText(properties.webhookSecret())) {
            throw new UnauthorizedException("Webhook signing secret is not configured");
        }
        if (!StringUtils.hasText(signature)) {
            throw new UnauthorizedException("Missing webhook signature");
        }

        byte[] suppliedSignature = parseSignature(signature);
        byte[] expectedSignature = hmac(rawBody, properties.webhookSecret());
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
    }

    private byte[] parseSignature(String signature) {
        String normalized = signature.trim();
        if (normalized.length() != SHA256_HEX_LENGTH) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
        try {
            return HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
    }

    private byte[] hmac(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(rawBody);
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

    private String normalizeEventName(String eventName) {
        if (!StringUtils.hasText(eventName)) {
            return "UNKNOWN";
        }
        return eventName.trim().toLowerCase(Locale.ROOT);
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
