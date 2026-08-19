package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionUpdateRequest;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;
import com.waypoint.backend.model.admin.AdminAuditEventEntity;

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
public class AdminSubscriptionService {
    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "renewsAt", "endsAt", "status", "plan");

    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final AdminAuditEventRepository auditEventRepository;

    public AdminSubscriptionService(
            SubscriptionRepository subscriptionRepository,
            PlanService planService,
            AdminAuditEventRepository auditEventRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planService = planService;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminSubscriptionResponse> subscriptions(
            UUID userId,
            String email,
            String provider,
            String plan,
            SubscriptionStatus status,
            String externalCustomerId,
            String externalSubscriptionId,
            Instant createdFrom,
            Instant createdTo,
            Instant updatedFrom,
            Instant updatedTo,
            int page,
            int size,
            String sort,
            String direction
    ) {
        AdminQuerySupport.validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        AdminQuerySupport.validateRange(updatedFrom, updatedTo, "updatedFrom", "updatedTo");

        Specification<SubscriptionEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (StringUtils.hasText(email)) predicates.add(cb.equal(cb.lower(root.get("user").get("email")), email.trim().toLowerCase(Locale.ROOT)));
            if (StringUtils.hasText(provider)) predicates.add(cb.equal(cb.upper(root.get("provider")), provider.trim().toUpperCase(Locale.ROOT)));
            if (StringUtils.hasText(plan)) predicates.add(cb.equal(cb.upper(root.get("plan")), plan.trim().toUpperCase(Locale.ROOT)));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (StringUtils.hasText(externalCustomerId)) predicates.add(cb.equal(root.get("externalCustomerId"), externalCustomerId.trim()));
            if (StringUtils.hasText(externalSubscriptionId)) predicates.add(cb.equal(root.get("externalSubscriptionId"), externalSubscriptionId.trim()));
            AdminQuerySupport.addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            AdminQuerySupport.addRange(predicates, cb, root.get("updatedAt"), updatedFrom, updatedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<SubscriptionEntity> result = subscriptionRepository.findAll(
                specification,
                AdminQuerySupport.pageable(page, size, sort, direction, "updatedAt", SORT_FIELDS)
        );
        return AdminQuerySupport.page(
                result.map(this::toResponse),
                AdminQuerySupport.sortOrDefault(sort, "updatedAt"),
                AdminQuerySupport.directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public AdminSubscriptionResponse subscription(UUID subscriptionId) {
        return toResponse(requireSubscription(subscriptionId));
    }

    @Transactional
    public AdminSubscriptionResponse updateSubscription(
            UUID subscriptionId,
            AdminSubscriptionUpdateRequest request,
            String adminId
    ) {
        if (request.status() == SubscriptionStatus.PREMIUM_SPECIAL) throw new InvalidRequestException("Use the Premium Special grant API for PREMIUM_SPECIAL access");
        if (request.clearRenewsAt() && request.renewsAt() != null) throw new InvalidRequestException("renewsAt and clearRenewsAt cannot be used together");
        if (request.clearEndsAt() && request.endsAt() != null) throw new InvalidRequestException("endsAt and clearEndsAt cannot be used together");
        if (request.status() == null && request.renewsAt() == null && request.endsAt() == null && !request.clearRenewsAt() && !request.clearEndsAt()) {
            throw new InvalidRequestException("At least one subscription field must be supplied");
        }

        SubscriptionEntity subscription = requireSubscription(subscriptionId);
        if (request.status() != null) subscription.setStatus(request.status());
        if (request.clearRenewsAt()) subscription.setRenewsAt(null); else if (request.renewsAt() != null) subscription.setRenewsAt(request.renewsAt());
        if (request.clearEndsAt()) subscription.setEndsAt(null); else if (request.endsAt() != null) subscription.setEndsAt(request.endsAt());

        SubscriptionEntity saved = subscriptionRepository.saveAndFlush(subscription);
        planService.synchronizeUserPlan(saved.getUser());
        audit(adminId, saved);
        return toResponse(saved);
    }

    private AdminSubscriptionResponse toResponse(SubscriptionEntity subscription) {
        return new AdminSubscriptionResponse(
                subscription.getId(), subscription.getUser().getId(), subscription.getUser().getEmail(),
                subscription.getProvider(), subscription.getExternalCustomerId(), subscription.getExternalSubscriptionId(),
                subscription.getExternalProductId(), subscription.getExternalVariantId(), subscription.getPlan(),
                subscription.getStatus(), subscription.getRenewsAt(), subscription.getEndsAt(),
                subscription.getCreatedAt(), subscription.getUpdatedAt()
        );
    }

    private SubscriptionEntity requireSubscription(UUID id) {
        return subscriptionRepository.findById(id).orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    private void audit(String adminId, SubscriptionEntity saved) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(adminId);
        event.setAction("UPDATE_SUBSCRIPTION");
        event.setResourceType("SUBSCRIPTION");
        event.setResourceId(saved.getId().toString());
        event.setDetails("status=" + saved.getStatus() + ", renewsAt=" + saved.getRenewsAt() + ", endsAt=" + saved.getEndsAt());
        auditEventRepository.save(event);
    }
}