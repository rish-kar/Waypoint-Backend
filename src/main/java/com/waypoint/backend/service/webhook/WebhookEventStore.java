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

import java.time.Instant;

@Service
public class WebhookEventStore {
    private final WebhookEventRepository webhookEventRepository;
    private final TransactionTemplate transactionTemplate;

    public WebhookEventStore(WebhookEventRepository webhookEventRepository, PlatformTransactionManager transactionManager) {
        this.webhookEventRepository = webhookEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public WebhookReception recordReceived(String eventHash, String payloadJson) {
        try {
            return transactionTemplate.execute(status -> webhookEventRepository.findByEventHashForUpdate(eventHash)
                    .map(this::claimExisting)
                    .orElseGet(() -> insertReceived(eventHash, payloadJson)));
        } catch (DataIntegrityViolationException exception) {
            return findExistingAfterRace(eventHash);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    public void markFailed(String eventHash, String eventName, String externalObjectId, String errorMessage) {
        WebhookEventEntity event = webhookEventRepository.findByEventHash(eventHash).orElseThrow();
        event.setEventName(normalizeEventName(eventName));
        event.setExternalObjectId(externalObjectId);
        event.setProcessingStatus(ProcessingStatus.FAILED);
        event.setErrorMessage(safeErrorMessage(errorMessage));
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    private WebhookReception insertReceived(String eventHash, String payloadJson) {
        WebhookEventEntity event = new WebhookEventEntity();
        event.setEventHash(eventHash);
        event.setPayloadJson(payloadJson);
        event.setEventName("UNKNOWN");
        event.setProcessingStatus(ProcessingStatus.RECEIVED);
        event.setReceivedAt(Instant.now());
        return new WebhookReception(webhookEventRepository.saveAndFlush(event), true, true);
    }

    private WebhookReception claimExisting(WebhookEventEntity event) {
        if (event.getProcessingStatus() != ProcessingStatus.FAILED) {
            return new WebhookReception(event, false, false);
        }
        event.setProcessingStatus(ProcessingStatus.RECEIVED);
        event.setErrorMessage(null);
        event.setProcessedAt(null);
        return new WebhookReception(webhookEventRepository.save(event), false, true);
    }

    private WebhookReception findExistingAfterRace(String eventHash) {
        for (int i = 0; i < 10; i++) {
            WebhookReception reception = transactionTemplate.execute(status -> webhookEventRepository
                    .findByEventHashForUpdate(eventHash)
                    .map(this::claimExisting)
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
}
