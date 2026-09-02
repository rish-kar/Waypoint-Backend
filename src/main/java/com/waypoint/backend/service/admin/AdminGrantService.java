package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantResponse;
import com.waypoint.backend.model.admin.PremiumSpecialSummaryResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
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
public class AdminGrantService {
    private static final String PREMIUM_SPECIAL = "PREMIUM_SPECIAL";
    private static final Set<String> SORT_FIELDS = Set.of("grantedAt", "updatedAt", "validUntil", "active");

    private final UserRepository userRepository;
    private final SpecialPremiumGrantRepository grantRepository;
    private final PlanService planService;
    private final AdminAuditEventRepository auditEventRepository;

    public AdminGrantService(
            UserRepository userRepository,
            SpecialPremiumGrantRepository grantRepository,
            PlanService planService,
            AdminAuditEventRepository auditEventRepository
    ) {
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.planService = planService;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public PremiumSpecialGrantResponse grantPremiumSpecial(UUID userId, PremiumSpecialGrantRequest request, String adminId) {
        Instant now = Instant.now();
        if (request.validUntil() != null && !request.validUntil().isAfter(now)) {
            throw new InvalidRequestException("validUntil must be in the future or omitted for lifetime access");
        }
        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = grantRepository.findByUserIdForUpdate(userId).orElseGet(SpecialPremiumGrantEntity::new);
        grant.setUser(user);
        grant.setActive(true);
        grant.setValidUntil(request.validUntil());
        grant.setReason(request.reason().trim());
        grant.setGrantedBy(adminId);
        grant.setGrantedAt(now);
        grant.setRevokedBy(null);
        grant.setRevokedAt(null);
        SpecialPremiumGrantEntity saved = grantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        audit(adminId, "GRANT_PREMIUM_SPECIAL", saved, "userId=" + userId + ", validUntil=" + saved.getValidUntil() + ", reason=" + saved.getReason());
        return toResponse(saved, now);
    }

    @Transactional
    public PremiumSpecialGrantResponse revokePremiumSpecial(UUID userId, String adminId) {
        UserEntity user = requireUser(userId);
        SpecialPremiumGrantEntity grant = grantRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Premium Special grant not found"));
        Instant now = Instant.now();
        grant.setActive(false);
        grant.setRevokedBy(adminId);
        grant.setRevokedAt(now);
        SpecialPremiumGrantEntity saved = grantRepository.saveAndFlush(grant);
        planService.synchronizeUserPlan(user);
        audit(adminId, "REVOKE_PREMIUM_SPECIAL", saved, "userId=" + userId);
        return toResponse(saved, now);
    }

    @Transactional(readOnly = true)
    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        Instant now = Instant.now();
        List<PremiumSpecialGrantResponse> users = grantRepository.findByActiveTrueOrderByGrantedAtDesc().stream()
                .filter(grant -> isActive(grant, now))
                .map(grant -> toResponse(grant, now))
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
        AdminQuerySupport.validateRange(grantedFrom, grantedTo, "grantedFrom", "grantedTo");
        Instant now = Instant.now();
        Specification<SpecialPremiumGrantEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (StringUtils.hasText(email)) predicates.add(cb.equal(cb.lower(root.get("user").get("email")), email.trim().toLowerCase(Locale.ROOT)));
            if (active != null && active) {
                predicates.add(cb.and(cb.isTrue(root.get("active")), cb.or(cb.isNull(root.get("validUntil")), cb.greaterThan(root.get("validUntil"), now))));
            } else if (active != null) {
                predicates.add(cb.or(cb.isFalse(root.get("active")), cb.and(cb.isNotNull(root.get("validUntil")), cb.lessThanOrEqualTo(root.get("validUntil"), now))));
            }
            if (StringUtils.hasText(grantedBy)) predicates.add(cb.equal(root.get("grantedBy"), grantedBy.trim()));
            AdminQuerySupport.addRange(predicates, cb, root.get("grantedAt"), grantedFrom, grantedTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<SpecialPremiumGrantEntity> result = grantRepository.findAll(
                specification,
                AdminQuerySupport.pageable(page, size, sort, direction, "grantedAt", SORT_FIELDS)
        );
        return AdminQuerySupport.page(
                result.map(grant -> toResponse(grant, now)),
                AdminQuerySupport.sortOrDefault(sort, "grantedAt"),
                AdminQuerySupport.directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public PremiumSpecialGrantResponse specialGrant(UUID grantId) {
        SpecialPremiumGrantEntity grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Premium Special grant not found"));
        return toResponse(grant, Instant.now());
    }

    private PremiumSpecialGrantResponse toResponse(SpecialPremiumGrantEntity grant, Instant now) {
        boolean active = isActive(grant, now);
        String status = active ? PREMIUM_SPECIAL : grant.isActive() ? "EXPIRED" : "REVOKED";
        return new PremiumSpecialGrantResponse(
                grant.getId(), grant.getUser().getId(), grant.getUser().getEmail(), PREMIUM_SPECIAL, status, active,
                grant.getValidUntil(), grant.getReason(), grant.getGrantedBy(), grant.getGrantedAt(), grant.getRevokedBy(), grant.getRevokedAt()
        );
    }

    private boolean isActive(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void audit(String adminId, String action, SpecialPremiumGrantEntity grant, String details) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(adminId);
        event.setAction(action);
        event.setResourceType("SPECIAL_GRANT");
        event.setResourceId(grant.getId().toString());
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}
