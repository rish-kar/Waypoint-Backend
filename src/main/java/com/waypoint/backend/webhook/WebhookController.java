package com.waypoint.backend.webhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/lemonsqueezy")
    public ResponseEntity<Void> lemonSqueezy(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        webhookService.process(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
