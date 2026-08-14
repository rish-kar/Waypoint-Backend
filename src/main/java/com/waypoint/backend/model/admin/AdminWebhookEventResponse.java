package com.waypoint.backend.model.admin;

import com.waypoint.backend.model.webhook.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminWebhookEventResponse(
        UUID id,
        String eventHash,
        String eventName,
        String externalObjectId,
        ProcessingStatus processingStatus,
        String payloadJson,
        String errorMessage,
        Instant receivedAt,
        Instant processedAt
) {
}
