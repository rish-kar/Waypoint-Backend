package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AdminQuerySupport {
    private static final int MAX_PAGE_SIZE = 500;

    private AdminQuerySupport() {
    }

    static PageRequest pageable(
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

    static String sortOrDefault(String sort, String defaultSort) {
        return StringUtils.hasText(sort) ? sort.trim() : defaultSort;
    }

    static String directionOrDefault(String direction) {
        return StringUtils.hasText(direction) ? direction.trim().toUpperCase(Locale.ROOT) : "DESC";
    }

    static <T> AdminPageResponse<T> page(Page<T> page, String sort, String direction) {
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

    static void validateRange(Instant from, Instant to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(fromName + " must be before or equal to " + toName);
        }
    }

    static void addRange(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Path<Instant> path,
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