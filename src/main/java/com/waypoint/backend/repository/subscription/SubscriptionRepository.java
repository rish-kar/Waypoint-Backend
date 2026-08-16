package com.waypoint.backend.repository.subscription;

import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionRepository
        extends JpaRepository<SubscriptionEntity, UUID>, JpaSpecificationExecutor<SubscriptionEntity> {
    List<SubscriptionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from SubscriptionEntity subscription where subscription.externalSubscriptionId = :externalSubscriptionId")
    Optional<SubscriptionEntity> findByExternalSubscriptionIdForUpdate(
            @Param("externalSubscriptionId") String externalSubscriptionId
    );

    @Query("""
            select distinct subscription.user.id
            from SubscriptionEntity subscription
            where (subscription.status = :trialStatus and subscription.trialEndsAt > :now)
               or (subscription.status in :renewingStatuses and subscription.renewsAt > :now)
               or (subscription.status = :cancelledStatus and subscription.endsAt > :now)
            """)
    Set<UUID> findPremiumUserIds(
            @Param("now") Instant now,
            @Param("trialStatus") SubscriptionStatus trialStatus,
            @Param("renewingStatuses") Set<SubscriptionStatus> renewingStatuses,
            @Param("cancelledStatus") SubscriptionStatus cancelledStatus
    );
}
