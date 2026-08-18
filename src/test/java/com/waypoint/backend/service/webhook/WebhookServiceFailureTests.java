package com.waypoint.backend.service.webhook;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceFailureTests {
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private WebhookEventStore webhookEventStore;
    private WebhookSubscriptionProcessor webhookSubscriptionProcessor;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookEventStore = mock(WebhookEventStore.class);
        webhookSubscriptionProcessor = mock(WebhookSubscriptionProcessor.class);
        LemonSqueezyProperties properties = new LemonSqueezyProperties(
                "test-api-key",
                "123",
                "111",
                "222",
                WEBHOOK_SECRET,
                "https://api.lemonsqueezy.com/v1"
        );
        webhookService = new WebhookService(
                properties,
                new ObjectMapper(),
                webhookEventStore,
                webhookSubscriptionProcessor
        );
        when(webhookEventStore.recordReceived(anyString(), anyString(), anyString(), any()))
                .thenReturn(new WebhookEventStore.WebhookReception(null, true, true));
    }

    @Test
    void internalProcessingFailureRemainsServerFailure() throws Exception {
        byte[] body = validBody();
        doThrow(new IllegalStateException("database unavailable"))
                .when(webhookSubscriptionProcessor)
                .process(any(JsonNode.class), eq("subscription_updated"));

        assertThatThrownBy(() -> webhookService.process(body, hmac(body)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to process webhook payload")
                .hasRootCauseMessage("database unavailable");
        verify(webhookEventStore).markFailed(
                anyString(),
                eq("subscription_updated"),
                eq("sub_123"),
                eq("database unavailable")
        );
    }

    @Test
    void malformedJsonRemainsInvalidRequest() throws Exception {
        byte[] body = "{".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> webhookService.process(body, hmac(body)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unable to parse webhook payload");
        verify(webhookEventStore).markFailed(
                anyString(),
                eq("UNKNOWN"),
                eq(null),
                anyString()
        );
    }

    @Test
    void domainValidationFailureRemainsInvalidRequest() throws Exception {
        byte[] body = validBody();
        doThrow(new InvalidRequestException("invalid subscription"))
                .when(webhookSubscriptionProcessor)
                .process(any(JsonNode.class), eq("subscription_updated"));

        assertThatThrownBy(() -> webhookService.process(body, hmac(body)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("invalid subscription");
    }

    private byte[] validBody() {
        return """
                {
                  "meta": {"event_name": "subscription_updated"},
                  "data": {"id": "sub_123", "type": "subscriptions", "attributes": {}}
                }
                """.getBytes(StandardCharsets.UTF_8);
    }

    private String hmac(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
