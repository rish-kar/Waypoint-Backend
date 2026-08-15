package com.waypoint.backend.service.webhook;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

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
        verifySignature(rawBody, signature);
        String eventHash = sha256Hex(rawBody);
        String payloadJson = new String(rawBody, StandardCharsets.UTF_8);
        WebhookEventStore.WebhookReception reception = webhookEventStore.recordReceived(eventHash, payloadJson);
        if (!reception.shouldProcess()) {
            return;
        }

        String eventName = "UNKNOWN";
        String externalObjectId = null;
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            eventName = text(payload.path("meta"), "event_name");
            externalObjectId = text(payload.path("data"), "id");
            webhookSubscriptionProcessor.process(payload, eventName);
            webhookEventStore.markProcessed(eventHash, eventName, externalObjectId);
        } catch (InvalidRequestException exception) {
            webhookEventStore.markFailed(eventHash, eventName, externalObjectId, safeMessage(exception));
            throw exception;
        } catch (JacksonException exception) {
            webhookEventStore.markFailed(eventHash, eventName, externalObjectId, safeMessage(exception));
            throw new InvalidRequestException("Unable to parse webhook payload");
        } catch (Exception exception) {
            webhookEventStore.markFailed(eventHash, eventName, externalObjectId, safeMessage(exception));
            throw new IllegalStateException("Unable to process webhook payload", exception);
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
