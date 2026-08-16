package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class WebhookEventStore {
    public static final Duration STALE_RECEIVED_AFTER = Duration.ofMinutes(5);

    private final WebhookEventRepository webhookEventRepository;
    private final TransactionTemplate transactionTemplate;

    public WebhookEventStore(WebhookEventRepository webhookEventRepository, PlatformTransactionManager transactionManager) {
        this.webhookEventRepository = webhookEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public WebhookReception recordReceived(String eventHash, String payloadJson) {
        return recordReceived(eventHash, payloadJson, "UNKNOWN", null);
    }

    public WebhookReception recordReceived(
            String eventHash,
            String payloadJson,
            String eventName,
            String externalObjectId
    ) {
        try {
            return transactionTemplate.execute(status -> webhookEventRepository.findByEventHashForUpdate(eventHash)
                    .map(event -> claimExisting(event, eventName, externalObjectId))
                    .orElseGet(() -> insertReceived(eventHash, payloadJson, eventName, externalObjectId)));
        } catch (DataIntegrityViolationException exception) {
            return findExistingAfterRace(eventHash, eventName, externalObjectId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryClaim claimForRecovery(UUID eventId) {
        WebhookEventEntity event = webhookEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !isStaleReceived(event)) {
            return null;
        }
        Instant now = Instant.now();
        event.setAttemptCount(Math.max(event.getAttemptCount(), 1) + 1);
        event.setLastAttemptAt(now);
        webhookEventRepository.save(event);
        return new RecoveryClaim(
                event.getId(),
                event.getEventHash(),
                event.getEventName(),
                event.getExternalObjectId()
        );
    }

    @Transactional
    public void markProcessed(String eventHash, String eventName, String externalObjectId) {
        WebhookEventEntity event = webhookEventRepository.findByEventHash(eventHash).orElseThrow();
        event.setEventName(normalizeEventName(eventName));
        event.setExternalObjectId(externalObjectId);
        event.setProcessingStatus(ProcessingStatus.PROCESSED);
        event.setErrorMessage(null);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIgnored(String eventHash, String eventName, String externalObjectId) {
        WebhookEventEntity event = webhookEventRepository.findByEventHash(eventHash).orElseThrow();
        event.setEventName(normalizeEventName(eventName));
        event.setExternalObjectId(externalObjectId);
        event.setProcessingStatus(ProcessingStatus.IGNORED);
        event.setErrorMessage(null);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventHash, String eventName, String externalObjectId, String errorMessage) {
        WebhookEventEntity event = webhookEventRepository.findByEventHash(eventHash).orElseThrow();
        event.setEventName(normalizeEventName(eventName));
        event.setExternalObjectId(externalObjectId);
        event.setProcessingStatus(ProcessingStatus.FAILED);
        event.setErrorMessage(safeErrorMessage(errorMessage));
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    private WebhookReception insertReceived(
            String eventHash,
            String payloadJson,
            String eventName,
            String externalObjectId
    ) {
        Instant now = Instant.now();
        WebhookEventEntity event = new WebhookEventEntity();
        event.setEventHash(eventHash);
        event.setPayloadJson(payloadJson);
        event.setEventName(normalizeEventName(eventName));
        event.setExternalObjectId(externalObjectId);
        event.setProcessingStatus(ProcessingStatus.RECEIVED);
        event.setReceivedAt(now);
        event.setAttemptCount(1);
        event.setLastAttemptAt(now);
        return new WebhookReception(webhookEventRepository.saveAndFlush(event), true, true);
    }

    private WebhookReception claimExisting(
            WebhookEventEntity event,
            String eventName,
            String externalObjectId
    ) {
        if (event.getProcessingStatus() == ProcessingStatus.FAILED || isStaleReceived(event)) {
            Instant now = Instant.now();
            event.setProcessingStatus(ProcessingStatus.RECEIVED);
            event.setErrorMessage(null);
            event.setProcessedAt(null);
            event.setAttemptCount(Math.max(event.getAttemptCount(), 1) + 1);
            event.setLastAttemptAt(now);
            updateMetadata(event, eventName, externalObjectId);
            return new WebhookReception(webhookEventRepository.save(event), false, true);
        }
        return new WebhookReception(event, false, false);
    }

    private void updateMetadata(WebhookEventEntity event, String eventName, String externalObjectId) {
        if (StringUtils.hasText(eventName) && !"UNKNOWN".equalsIgnoreCase(eventName)) {
            event.setEventName(normalizeEventName(eventName));
        }
        if (StringUtils.hasText(externalObjectId)) {
            event.setExternalObjectId(externalObjectId);
        }
    }

    private boolean isStaleReceived(WebhookEventEntity event) {
        if (event.getProcessingStatus() != ProcessingStatus.RECEIVED) {
            return false;
        }
        Instant lastAttemptAt = event.getLastAttemptAt();
        return lastAttemptAt == null || !lastAttemptAt.isAfter(Instant.now().minus(STALE_RECEIVED_AFTER));
    }

    private WebhookReception findExistingAfterRace(
            String eventHash,
            String eventName,
            String externalObjectId
    ) {
        for (int i = 0; i < 10; i++) {
            WebhookReception reception = transactionTemplate.execute(status -> webhookEventRepository
                    .findByEventHashForUpdate(eventHash)
                    .map(event -> claimExisting(event, eventName, externalObjectId))
                    .orElse(null));
            if (reception != null) {
                return reception;
            }
            sleepBriefly();
        }
        throw new DataIntegrityViolationException("Duplicate webhook event hash exists but could not be loaded");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeEventName(String eventName) {
        return StringUtils.hasText(eventName) ? eventName : "UNKNOWN";
    }

    private String safeErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "Webhook processing failed";
        }
        return errorMessage.length() <= 500 ? errorMessage : errorMessage.substring(0, 500);
    }

    public record WebhookReception(WebhookEventEntity event, boolean created, boolean shouldProcess) {
    }

    public record RecoveryClaim(UUID eventId, String eventHash, String eventName, String externalObjectId) {
    }
}