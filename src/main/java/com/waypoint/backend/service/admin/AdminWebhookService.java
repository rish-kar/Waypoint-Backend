package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventUpdateRequest;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminWebhookService {
    private static final Set<String> SORT_FIELDS = Set.of("receivedAt", "processedAt", "eventName", "processingStatus");

    private final WebhookEventRepository webhookEventRepository;
    private final AdminAuditEventRepository auditEventRepository;

    public AdminWebhookService(
            WebhookEventRepository webhookEventRepository,
            AdminAuditEventRepository auditEventRepository
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminWebhookEventResponse> webhookEvents(
            String eventName,
            ProcessingStatus processingStatus,
            String externalObjectId,
            Instant receivedFrom,
            Instant receivedTo,
            boolean includePayload,
            int page,
            int size,
            String sort,
            String direction
    ) {
        AdminQuerySupport.validateRange(receivedFrom, receivedTo, "receivedFrom", "receivedTo");
        Specification<WebhookEventEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(eventName)) predicates.add(cb.equal(cb.lower(root.get("eventName")), eventName.trim().toLowerCase(Locale.ROOT)));
            if (processingStatus != null) predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            if (StringUtils.hasText(externalObjectId)) predicates.add(cb.equal(root.get("externalObjectId"), externalObjectId.trim()));
            AdminQuerySupport.addRange(predicates, cb, root.get("receivedAt"), receivedFrom, receivedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<WebhookEventEntity> result = webhookEventRepository.findAll(
                specification,
                AdminQuerySupport.pageable(page, size, sort, direction, "receivedAt", SORT_FIELDS)
        );
        return AdminQuerySupport.page(
                result.map(event -> toResponse(event, includePayload)),
                AdminQuerySupport.sortOrDefault(sort, "receivedAt"),
                AdminQuerySupport.directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public AdminWebhookEventResponse webhookEvent(UUID eventId) {
        return toResponse(requireEvent(eventId), true);
    }

    @Transactional
    public AdminWebhookEventResponse updateWebhookEvent(
            UUID eventId,
            AdminWebhookEventUpdateRequest request,
            String adminId
    ) {
        if (request.clearErrorMessage() && request.errorMessage() != null) throw new InvalidRequestException("errorMessage and clearErrorMessage cannot be used together");
        if (request.clearProcessedAt() && request.processedAt() != null) throw new InvalidRequestException("processedAt and clearProcessedAt cannot be used together");
        if (request.processingStatus() == null && request.errorMessage() == null && request.processedAt() == null && !request.clearErrorMessage() && !request.clearProcessedAt()) {
            throw new InvalidRequestException("At least one webhook event field must be supplied");
        }

        WebhookEventEntity event = requireEvent(eventId);
        if (request.processingStatus() != null) event.setProcessingStatus(request.processingStatus());
        if (request.clearErrorMessage()) event.setErrorMessage(null); else if (request.errorMessage() != null) event.setErrorMessage(request.errorMessage());
        if (request.clearProcessedAt()) event.setProcessedAt(null); else if (request.processedAt() != null) event.setProcessedAt(request.processedAt());
        WebhookEventEntity saved = webhookEventRepository.saveAndFlush(event);
        audit(adminId, saved);
        return toResponse(saved, true);
    }

    private AdminWebhookEventResponse toResponse(WebhookEventEntity event, boolean includePayload) {
        return new AdminWebhookEventResponse(
                event.getId(), event.getEventHash(), event.getEventName(), event.getExternalObjectId(),
                event.getProcessingStatus(), includePayload ? event.getPayloadJson() : null, event.getErrorMessage(),
                event.getReceivedAt(), event.getProcessedAt()
        );
    }

    private WebhookEventEntity requireEvent(UUID id) {
        return webhookEventRepository.findById(id).orElseThrow(() -> new NotFoundException("Webhook event not found"));
    }

    private void audit(String adminId, WebhookEventEntity saved) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(adminId);
        event.setAction("UPDATE_WEBHOOK_EVENT");
        event.setResourceType("WEBHOOK_EVENT");
        event.setResourceId(saved.getId().toString());
        event.setDetails("processingStatus=" + saved.getProcessingStatus() + ", processedAt=" + saved.getProcessedAt());
        auditEventRepository.save(event);
    }
}