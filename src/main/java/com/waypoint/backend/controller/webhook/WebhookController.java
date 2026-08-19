package com.waypoint.backend.controller.webhook;

import com.waypoint.backend.service.webhook.WebhookService;
import com.waypoint.backend.utilities.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private static final int MAX_WEBHOOK_PAYLOAD_BYTES = 256 * 1024;

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/lemonsqueezy")
    public ResponseEntity<Void> lemonSqueezy(
            HttpServletRequest request,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_WEBHOOK_PAYLOAD_BYTES) {
            throw payloadTooLarge();
        }

        byte[] rawBody = request.getInputStream().readNBytes(MAX_WEBHOOK_PAYLOAD_BYTES + 1);
        if (rawBody.length > MAX_WEBHOOK_PAYLOAD_BYTES) {
            throw payloadTooLarge();
        }

        webhookService.process(rawBody, signature);
        return ResponseEntity.ok().build();
    }

    private ApiException payloadTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "Webhook payload exceeds 256 KiB"
        );
    }
}
