package com.waypoint.backend.service.admin;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.admin.AdminAuditEventResponse;
import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminPlanResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.plan.PlanRepository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
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

@Service
public class AdminCatalogService {
    private static final Set<String> AUDIT_SORT_FIELDS = Set.of("createdAt", "adminId", "action", "resourceType");

    private final PlanRepository planRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final LemonSqueezyProperties lemonSqueezyProperties;

    public AdminCatalogService(
            PlanRepository planRepository,
            AdminAuditEventRepository auditEventRepository,
            LemonSqueezyProperties lemonSqueezyProperties
    ) {
        this.planRepository = planRepository;
        this.auditEventRepository = auditEventRepository;
        this.lemonSqueezyProperties = lemonSqueezyProperties;
    }

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> plans() {
        return planRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream().map(this::toPlanResponse).toList();
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
        AdminQuerySupport.validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        Specification<AdminAuditEventEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(adminId)) predicates.add(cb.equal(root.get("adminId"), adminId.trim()));
            if (StringUtils.hasText(action)) predicates.add(cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase(Locale.ROOT)));
            if (StringUtils.hasText(resourceType)) predicates.add(cb.equal(cb.upper(root.get("resourceType")), resourceType.trim().toUpperCase(Locale.ROOT)));
            if (StringUtils.hasText(resourceId)) predicates.add(cb.equal(root.get("resourceId"), resourceId.trim()));
            AdminQuerySupport.addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AdminAuditEventEntity> result = auditEventRepository.findAll(
                specification,
                AdminQuerySupport.pageable(page, size, sort, direction, "createdAt", AUDIT_SORT_FIELDS)
        );
        return AdminQuerySupport.page(
                result.map(this::toAuditResponse),
                AdminQuerySupport.sortOrDefault(sort, "createdAt"),
                AdminQuerySupport.directionOrDefault(direction)
        );
    }

    private AdminPlanResponse toPlanResponse(PlanEntity plan) {
        return new AdminPlanResponse(
                plan.getCode(), plan.getDisplayName(), plan.getBillingInterval(), plan.getPriceCents(), plan.getCurrency(),
                plan.isPremium(), plan.isActive(), providerVariantId(plan.getCode()), plan.getCreatedAt(), plan.getUpdatedAt()
        );
    }

    private String providerVariantId(PlanCode planCode) {
        return switch (planCode) {
            case PREMIUM_MONTHLY -> lemonSqueezyProperties.monthlyVariantId();
            case PREMIUM_ANNUAL -> lemonSqueezyProperties.annualVariantId();
            default -> null;
        };
    }

    private AdminAuditEventResponse toAuditResponse(AdminAuditEventEntity event) {
        return new AdminAuditEventResponse(
                event.getId(), event.getAdminId(), event.getAction(), event.getResourceType(), event.getResourceId(),
                event.getDetails(), event.getCreatedAt()
        );
    }
}
