package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminUserResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.NotFoundException;

import jakarta.persistence.criteria.JoinType;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "lastLoginAt", "email", "displayName");

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public AdminUserService(UserRepository userRepository, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
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
        AdminQuerySupport.validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        AdminQuerySupport.validateRange(lastLoginFrom, lastLoginTo, "lastLoginFrom", "lastLoginTo");

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
            AdminQuerySupport.addRange(predicates, cb, root.get("createdAt"), createdFrom, createdTo);
            AdminQuerySupport.addRange(predicates, cb, root.get("lastLoginAt"), lastLoginFrom, lastLoginTo);
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<UserEntity> result = userRepository.findAll(
                specification,
                AdminQuerySupport.pageable(page, size, sort, direction, "createdAt", SORT_FIELDS)
        );
        Set<UUID> userIds = result.getContent().stream().map(UserEntity::getId).collect(Collectors.toSet());
        Map<UUID, SubscriptionSnapshot> subscriptions = subscriptionService.currentForUsers(userIds, Instant.now());
        Page<AdminUserResponse> mapped = result.map(user -> toResponse(user, subscriptions.get(user.getId())));
        return AdminQuerySupport.page(
                mapped,
                AdminQuerySupport.sortOrDefault(sort, "createdAt"),
                AdminQuerySupport.directionOrDefault(direction)
        );
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(UUID userId) {
        UserEntity user = requireUser(userId);
        return toResponse(user, subscriptionService.current(userId));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse userByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidRequestException("email is required");
        }
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(user, subscriptionService.current(user.getId()));
    }

    private AdminUserResponse toResponse(UserEntity user, SubscriptionSnapshot subscription) {
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

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}