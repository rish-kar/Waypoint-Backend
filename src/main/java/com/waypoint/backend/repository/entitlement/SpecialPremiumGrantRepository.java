package com.waypoint.backend.repository.entitlement;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SpecialPremiumGrantRepository
        extends JpaRepository<SpecialPremiumGrantEntity, UUID>, JpaSpecificationExecutor<SpecialPremiumGrantEntity> {
    Optional<SpecialPremiumGrantEntity> findByUserId(UUID userId);

    List<SpecialPremiumGrantEntity> findByActiveTrueOrderByGrantedAtDesc();

    @Query("""
            select grant
            from SpecialPremiumGrantEntity grant
            where grant.user.id in :userIds
              and grant.active = true
              and (grant.validUntil is null or grant.validUntil > :now)
            """)
    List<SpecialPremiumGrantEntity> findActiveForUsers(
            @Param("userIds") Set<UUID> userIds,
            @Param("now") Instant now
    );

    @Query("""
            select grant.user.id
            from SpecialPremiumGrantEntity grant
            where grant.active = true
              and (grant.validUntil is null or grant.validUntil > :now)
            """)
    Set<UUID> findActiveUserIds(@Param("now") Instant now);

    @Query("""
            select count(grant)
            from SpecialPremiumGrantEntity grant
            where grant.active = true
              and (grant.validUntil is null or grant.validUntil > :now)
            """)
    long countActiveAt(@Param("now") Instant now);
}