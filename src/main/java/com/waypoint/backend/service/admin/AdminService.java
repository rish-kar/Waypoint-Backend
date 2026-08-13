package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.admin.AdminAuditEventResponse;
import com.waypoint.backend.model.admin.AdminOverviewResponse;
import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminPlanResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionUpdateRequest;
import com.waypoint.backend.model.admin.AdminUserResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventUpdateRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantResponse;
import com.waypoint.backend.model.admin.PremiumSpecialSummaryResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class AdminService {
    private static final int MAX_PAGE_SIZE = 500;
    private static final String PREMIUM_SPECIAL = "PREMIUM_SPECIAL";

    private static final Set<String> USER_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "lastLoginAt", "email", "displayName");
    private static final Set<String> SUBSCRIPTION_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "renewsAt", "endsAt", "status", "plan");
    private static final Set<String> GRANT_SORT_FIELDS =
            Set.of("grantedAt", "updatedAt", "validUntil", "active");
    private static final Set<String> WEBHOOK_SORT_FIELDS =
            Set.of("receivedAt", "processedAt", "eventName", "processingStatus");
    private static final Set<String> AUDIT_SORT_FIELDS =
            Set.of("createdAt", "adminId", "action", "resourceType");

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PlanRepository planRepository;
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;

    public AdminService(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository specialPremiumGrantRepository,
            WebhookEventRepository webhookEventRepository,
            PlanRepository planRepository,
            AdminAuditEventRepository adminAuditEventRepository,
            SubscriptionService subscriptionService,
            PlanService planService
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.planRepository = planRepository;
        this.adminAuditEventRepository = adminAuditEventRepository;
        this.subscriptionService = subscriptionService;
        this.planService = planService;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        Instant now = Instant.now();
        long premiumUsers = userRepository.count((root, query, cb) ->
                cb.isTrue(root.join("plan", JoinType.LEFT).get("premium")));
        long activeSpecialGrants = specialPremiumGrantRepository.count((root, query, cb) ->
                cb.and(
                        cb.isTrue(root.get("active")),
                        cb.or(
                                cb.isNull(root.get("validUntil")),
                                cb.greaterThan(root.get("validUntil"), now)
                        )
                ));
        long failedWebhookEvents = webhookEventRepository.count((root, query, cb) ->
                cb.equal(root.get("processingStatus"), ProcessingStatus.FAILED));

        return new AdminOverviewResponse(
                userRepository.count(),
                premiumUsers,
                subscriptionRepository.count(),
                activeSpecialGrants,
                webhookEventRepository.count(),
                failedWebhookEvents
        );
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserResponse> users(
            String q,
            String provider,
            PlanCode plan,
            Boolean premium,
            Instant createdFrom,
            Instant createdTo,
            Instant lastLoginFrom,
            Instant lastLoginTo,
            int page,
            int size,
            String sort,
            String direction
    ) {
        validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        validateRange(lastLoginFrom, lastLoginTo, "lastLoginFrom", "lastLoginTo");

        Specification<UserEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("displayName")), like)
                ));
            }
            if (StringUtils.hasText(provider)) {
                predicates.add(cb.equal(cb.upper(root.get("provider")), provider.trim().toUpperCase(Locale.ROOT)));
            }
            if (plan != null) {
                predicates.add(cb.equal(root.join("plan", JoinType.LEFT).get("code"), plan));
            }
            if (premium != null) {
                predicates.add(cb.equal(root.join("plan", JoinType.LEFT).get("premium"), premium));
            }
            addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            addRange(predicates, cb, root.get("lastLoginAt"), lastLoginFrom, lastLoginTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<UserEntity> result = userRepository.findAll(
                specification,
                pageable(page, size, sort, direction, "createdAt", USER_SORT_FIELDS)
        );
        return page(result.map(this::toUserResponse), sortOrDefault(sort, "createdAt"), directionOrDefault(direction));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(UUID userId) {
        return toUserResponse(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse userByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidRequestException("email is required");
        }
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toUserResponse(user);
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
        validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        validateRange(updatedFrom, updatedTo, "updatedFrom", "updatedTo");

        Specification<SubscriptionEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (StringUtils.hasText(email)) {
                predicates.add(cb.equal(
                        cb.lower(root.get("user").get("email")),
                        email.trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (StringUtils.hasText(provider)) {
                predicates.add(cb.equal(cb.upper(root.get("provider")), provider.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(plan)) {
                predicates.add(cb.equal(cb.upper(root.get("plan")), plan.trim().toUpperCase(Locale.ROOT)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(externalCustomerId)) {
                predicates.add(cb.equal(root.get("externalCustomerId"), externalCustomerId.trim()));
            }
            if (StringUtils.hasText(externalSubscriptionId)) {
                predicates.add(cb.equal(root.get("externalSubscriptionId"), externalSubscriptionId.trim()));
            }
            addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            addRange(predicates, cb, root.get("updatedAt"), updatedFrom, updatedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<SubscriptionEntity> result = subscriptionRepository.findAll(
                specification,
                pageable(page, size, sort, direction, "updatedAt", SUBSCRIPTION_SORT_FIELDS)
        );
        return page(
                result.map(this::toSubscriptionResponse),
                sortOrDefault(sort, "updatedAt"),
                directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public AdminSubscriptionResponse subscription(UUID subscriptionId) {
        return toSubscriptionResponse(requireSubscription(subscriptionId));
    }

    @Transactional
    public AdminSubscriptionResponse updateSubscription(
            UUID subscriptionId,
            AdminSubscriptionUpdateRequest request,
            String adminId
    ) {
        if (request.status() == SubscriptionStatus.PREMIUM_SPECIAL) {
            throw new InvalidRequestException("Use the Premium Special grant API for PREMIUM_SPECIAL access");
        }
        if (request.clearRenewsAt() && request.renewsAt() != null) {
            throw new InvalidRequestException("renewsAt and clearRenewsAt cannot be used together");
        }
        if (request.clearEndsAt() && request.endsAt() != null) {
            throw new InvalidRequestException("endsAt and clearEndsAt cannot be used together");
        }
        if (request.status() == null
                && request.renewsAt() == null
                && request.endsAt() == null
                && !request.clearRenewsAt()
                && !request.clearEndsAt()) {
            throw new InvalidRequestException("At least one subscription field must be supplied");
        }

        SubscriptionEntity subscription = requireSubscription(subscriptionId);
        if (request.status() != null) {
            subscription.setStatus(request.status());
        }
        if (request.clearRenewsAt()) {
            subscription.setRenewsAt(null);
        } else if (request.renewsAt() != null) {
            subscription.setRenewsAt(request.renewsAt());
        }
        if (request.clearEndsAt()) {
            subscription.setEndsAt(null);
        } else if (request.endsAt() != null) {
            subscription.setEndsAt(request.endsAt());
        }

        SubscriptionEntity saved = subscriptionRepository.saveAndFlush(subscription);
        planService.synchronizeUserPlan(saved.getUser());
        audit(
                adminId,
                "UPDATE_SUBSCRIPTION",
                "SUBSCRIPTION",
                saved.getId().toString(),
                "status=" + saved.getStatus()
                        + ", renewsAt=" + saved.getRenewsAt()
                        + ", endsAt=" + saved.getEndsAt()
        );
        return toSubscriptionResponse(saved);
    }

    @Transactional
    public PremiumSpecialGrantResponse grantPremiumSpecial(
            UUID userId,
            PremiumSpecialGrantRequest request,
            String adminId
    ) {
        Instant now = Instant.now();
        if (request.validUntil() != null && !request.validUntil().isAfter(now)) {
            throw new InvalidRequestException("validUntil must be in the future or omitted for lifetime access");
        }

        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = specialPremiumGrantRepository.findByUserId(userId)
                .orElseGet(SpecialPremiumGrantEntity::new);
        grant.setUser(user);
        grant.setActive(true);
        grant.setValidUntil(request.validUntil());
        grant.setReason(request.reason().trim());
        grant.setGrantedBy(adminId);
        grant.setGrantedAt(now);
        grant.setRevokedBy(null);
        grant.setRevokedAt(null);
        SpecialPremiumGrantEntity saved = specialPremiumGrantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        audit(
                adminId,
                "GRANT_PREMIUM_SPECIAL",
                "SPECIAL_GRANT",
                saved.getId().toString(),
                "userId=" + userId + ", validUntil=" + saved.getValidUntil() + ", reason=" + saved.getReason()
        );
        return toGrantResponse(saved, now);
    }

    @Transactional
    public PremiumSpecialGrantResponse revokePremiumSpecial(UUID userId, String adminId) {
        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = specialPremiumGrantRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Premium Special grant not found"));
        Instant now = Instant.now();
        grant.setActive(false);
        grant.setRevokedBy(adminId);
        grant.setRevokedAt(now);
        SpecialPremiumGrantEntity saved = specialPremiumGrantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        audit(
                adminId,
                "REVOKE_PREMIUM_SPECIAL",
                "SPECIAL_GRANT",
                saved.getId().toString(),
                "userId=" + userId
        );
        return toGrantResponse(saved, now);
    }

    @Transactional(readOnly = true)
    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        Instant now = Instant.now();
        List<PremiumSpecialGrantResponse> users = specialPremiumGrantRepository.findByActiveTrueOrderByGrantedAtDesc()
                .stream()
                .filter(grant -> isActive(grant, now))
                .map(grant -> toGrantResponse(grant, now))
                .toList();
        return new PremiumSpecialSummaryResponse(users.size(), users);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<PremiumSpecialGrantResponse> specialGrants(
            UUID userId,
            String email,
            Boolean active,
            String grantedBy,
            Instant grantedFrom,
            Instant grantedTo,
            int page,
            int size,
            String sort,
            String direction
    ) {
        validateRange(grantedFrom, grantedTo, "grantedFrom", "grantedTo");
        Instant now = Instant.now();

        Specification<SpecialPremiumGrantEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (StringUtils.hasText(email)) {
                predicates.add(cb.equal(
                        cb.lower(root.get("user").get("email")),
                        email.trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (active != null && active) {
                predicates.add(cb.and(
                        cb.isTrue(root.get("active")),
                        cb.or(
                                cb.isNull(root.get("validUntil")),
                                cb.greaterThan(root.get("validUntil"), now)
                        )
                ));
            } else if (active != null) {
                predicates.add(cb.or(
                        cb.isFalse(root.get("active")),
                        cb.and(
                                cb.isNotNull(root.get("validUntil")),
                                cb.lessThanOrEqualTo(root.get("validUntil"), now)
                        )
                ));
            }
            if (StringUtils.hasText(grantedBy)) {
                predicates.add(cb.equal(root.get("grantedBy"), grantedBy.trim()));
            }
            addRange(predicates, cb, root.get("grantedAt"), grantedFrom, grantedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<SpecialPremiumGrantEntity> result = specialPremiumGrantRepository.findAll(
                specification,
                pageable(page, size, sort, direction, "grantedAt", GRANT_SORT_FIELDS)
        );
        return page(
                result.map(grant -> toGrantResponse(grant, now)),
                sortOrDefault(sort, "grantedAt"),
                directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public PremiumSpecialGrantResponse specialGrant(UUID grantId) {
        SpecialPremiumGrantEntity grant = specialPremiumGrantRepository.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Premium Special grant not found"));
        return toGrantResponse(grant, Instant.now());
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
        validateRange(receivedFrom, receivedTo, "receivedFrom", "receivedTo");
        Specification<WebhookEventEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(eventName)) {
                predicates.add(cb.equal(cb.lower(root.get("eventName")), eventName.trim().toLowerCase(Locale.ROOT)));
            }
            if (processingStatus != null) {
                predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            }
            if (StringUtils.hasText(externalObjectId)) {
                predicates.add(cb.equal(root.get("externalObjectId"), externalObjectId.trim()));
            }
            addRange(predicates, cb, root.get("receivedAt"), receivedFrom, receivedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<WebhookEventEntity> result = webhookEventRepository.findAll(
                specification,
                pageable(page, size, sort, direction, "receivedAt", WEBHOOK_SORT_FIELDS)
        );
        return page(
                result.map(event -> toWebhookEventResponse(event, includePayload)),
                sortOrDefault(sort, "receivedAt"),
                directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public AdminWebhookEventResponse webhookEvent(UUID eventId) {
        return toWebhookEventResponse(requireWebhookEvent(eventId), true);
    }

    @Transactional
    public AdminWebhookEventResponse updateWebhookEvent(
            UUID eventId,
            AdminWebhookEventUpdateRequest request,
            String adminId
    ) {
        if (request.clearErrorMessage() && request.errorMessage() != null) {
            throw new InvalidRequestException("errorMessage and clearErrorMessage cannot be used together");
        }
        if (request.clearProcessedAt() && request.processedAt() != null) {
            throw new InvalidRequestException("processedAt and clearProcessedAt cannot be used together");
        }
        if (request.processingStatus() == null
                && request.errorMessage() == null
                && request.processedAt() == null
                && !request.clearErrorMessage()
                && !request.clearProcessedAt()) {
            throw new InvalidRequestException("At least one webhook event field must be supplied");
        }

        WebhookEventEntity event = requireWebhookEvent(eventId);
        if (request.processingStatus() != null) {
            event.setProcessingStatus(request.processingStatus());
        }
        if (request.clearErrorMessage()) {
            event.setErrorMessage(null);
        } else if (request.errorMessage() != null) {
            event.setErrorMessage(request.errorMessage());
        }
        if (request.clearProcessedAt()) {
            event.setProcessedAt(null);
        } else if (request.processedAt() != null) {
            event.setProcessedAt(request.processedAt());
        }

        WebhookEventEntity saved = webhookEventRepository.saveAndFlush(event);
        audit(
                adminId,
                "UPDATE_WEBHOOK_EVENT",
                "WEBHOOK_EVENT",
                saved.getId().toString(),
                "processingStatus=" + saved.getProcessingStatus()
                        + ", processedAt=" + saved.getProcessedAt()
        );
        return toWebhookEventResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> plans() {
        return planRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminAuditEventResponse> auditEvents(
            String adminId,
            String action,
            String resourceType,
            String resourceId,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort,
            String direction
    ) {
        validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        Specification<AdminAuditEventEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(adminId)) {
                predicates.add(cb.equal(root.get("adminId"), adminId.trim()));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(resourceType)) {
                predicates.add(cb.equal(
                        cb.upper(root.get("resourceType")),
                        resourceType.trim().toUpperCase(Locale.ROOT)
                ));
            }
            if (StringUtils.hasText(resourceId)) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId.trim()));
            }
            addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<AdminAuditEventEntity> result = adminAuditEventRepository.findAll(
                specification,
                pageable(page, size, sort, direction, "createdAt", AUDIT_SORT_FIELDS)
        );
        return page(
                result.map(this::toAuditResponse),
                sortOrDefault(sort, "createdAt"),
                directionOrDefault(direction)
        );
    }

    private AdminUserResponse toUserResponse(UserEntity user) {
        SubscriptionSnapshot subscription = subscriptionService.current(user.getId());
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                user.getProvider(),
                user.getProviderUserId(),
                user.getPlan() == null ? null : user.getPlan().getCode(),
                subscription.planCode(),
                subscription.status(),
                subscription.premium(),
                subscription.validUntil(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }

    private AdminSubscriptionResponse toSubscriptionResponse(SubscriptionEntity subscription) {
        return new AdminSubscriptionResponse(
                subscription.getId(),
                subscription.getUser().getId(),
                subscription.getUser().getEmail(),
                subscription.getProvider(),
                subscription.getExternalCustomerId(),
                subscription.getExternalSubscriptionId(),
                subscription.getExternalProductId(),
                subscription.getExternalVariantId(),
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getRenewsAt(),
                subscription.getEndsAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }

    private PremiumSpecialGrantResponse toGrantResponse(SpecialPremiumGrantEntity grant, Instant now) {
        boolean active = isActive(grant, now);
        String status = active
                ? PREMIUM_SPECIAL
                : grant.isActive() ? "EXPIRED" : "REVOKED";
        return new PremiumSpecialGrantResponse(
                grant.getId(),
                grant.getUser().getId(),
                grant.getUser().getEmail(),
                PREMIUM_SPECIAL,
                status,
                active,
                grant.getValidUntil(),
                grant.getReason(),
                grant.getGrantedBy(),
                grant.getGrantedAt(),
                grant.getRevokedBy(),
                grant.getRevokedAt()
        );
    }

    private AdminWebhookEventResponse toWebhookEventResponse(WebhookEventEntity event, boolean includePayload) {
        return new AdminWebhookEventResponse(
                event.getId(),
                event.getEventHash(),
                event.getEventName(),
                event.getExternalObjectId(),
                event.getProcessingStatus(),
                includePayload ? event.getPayloadJson() : null,
                event.getErrorMessage(),
                event.getReceivedAt(),
                event.getProcessedAt()
        );
    }

    private AdminPlanResponse toPlanResponse(PlanEntity plan) {
        return new AdminPlanResponse(
                plan.getCode(),
                plan.getDisplayName(),
                plan.getBillingInterval(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.isPremium(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private AdminAuditEventResponse toAuditResponse(AdminAuditEventEntity event) {
        return new AdminAuditEventResponse(
                event.getId(),
                event.getAdminId(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getDetails(),
                event.getCreatedAt()
        );
    }

    private boolean isActive(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private SubscriptionEntity requireSubscription(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    private WebhookEventEntity requireWebhookEvent(UUID eventId) {
        return webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Webhook event not found"));
    }

    private void audit(
            String adminId,
            String action,
            String resourceType,
            String resourceId,
            String details
    ) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(adminId);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setDetails(details);
        adminAuditEventRepository.save(event);
    }

    private PageRequest pageable(
            int page,
            int size,
            String sort,
            String direction,
            String defaultSort,
            Set<String> allowedSortFields
    ) {
        if (page < 0) {
            throw new InvalidRequestException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String resolvedSort = sortOrDefault(sort, defaultSort);
        if (!allowedSortFields.contains(resolvedSort)) {
            throw new InvalidRequestException("Unsupported sort field: " + resolvedSort);
        }
        Sort.Direction resolvedDirection;
        try {
            resolvedDirection = Sort.Direction.fromString(directionOrDefault(direction));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("direction must be ASC or DESC");
        }
        return PageRequest.of(page, size, Sort.by(resolvedDirection, resolvedSort));
    }

    private String sortOrDefault(String sort, String defaultSort) {
        return StringUtils.hasText(sort) ? sort.trim() : defaultSort;
    }

    private String directionOrDefault(String direction) {
        return StringUtils.hasText(direction) ? direction.trim().toUpperCase(Locale.ROOT) : "DESC";
    }

    private <T> AdminPageResponse<T> page(Page<T> page, String sort, String direction) {
        return new AdminPageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                sort,
                direction
        );
    }

    private void validateRange(Instant from, Instant to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(fromName + " must be before or equal to " + toName);
        }
    }

    private void addRange(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<Instant> path,
            Instant from,
            Instant to
    ) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(path, to));
        }
    }
}
