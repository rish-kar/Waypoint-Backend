package com.waypoint.backend.repository.subscription;

import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    Optional<SubscriptionEntity> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from SubscriptionEntity subscription where subscription.externalSubscriptionId = :externalSubscriptionId")
    Optional<SubscriptionEntity> findByExternalSubscriptionIdForUpdate(
            @Param("externalSubscriptionId") String externalSubscriptionId
    );

    @Query("""
            select subscription
            from SubscriptionEntity subscription
            where subscription.user.id = :userId
              and (
                    (subscription.status = :trialStatus and subscription.trialEndsAt > :now)
                 or (subscription.status in :renewingStatuses and subscription.renewsAt > :now)
                 or (subscription.status = :cancelledStatus and subscription.endsAt > :now)
              )
            order by case
                         when subscription.status = :trialStatus then subscription.trialEndsAt
                         when subscription.status in :renewingStatuses then subscription.renewsAt
                         when subscription.status = :cancelledStatus then subscription.endsAt
                     end desc,
                     subscription.updatedAt desc
            """)
    List<SubscriptionEntity> findCurrentPremiumCandidates(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("trialStatus") SubscriptionStatus trialStatus,
            @Param("renewingStatuses") Set<SubscriptionStatus> renewingStatuses,
            @Param("cancelledStatus") SubscriptionStatus cancelledStatus,
            Pageable pageable
    );

    @Query("""
            select subscription
            from SubscriptionEntity subscription
            where subscription.user.id in :userIds
              and (
                    (subscription.status = :trialStatus and subscription.trialEndsAt > :now)
                 or (subscription.status in :renewingStatuses and subscription.renewsAt > :now)
                 or (subscription.status = :cancelledStatus and subscription.endsAt > :now)
              )
            """)
    List<SubscriptionEntity> findCurrentPremiumCandidatesForUsers(
            @Param("userIds") Set<UUID> userIds,
            @Param("now") Instant now,
            @Param("trialStatus") SubscriptionStatus trialStatus,
            @Param("renewingStatuses") Set<SubscriptionStatus> renewingStatuses,
            @Param("cancelledStatus") SubscriptionStatus cancelledStatus
    );

    @Query("""
            select subscription
            from SubscriptionEntity subscription
            where subscription.user.id in :userIds
              and subscription.updatedAt = (
                    select max(latest.updatedAt)
                    from SubscriptionEntity latest
                    where latest.user.id = subscription.user.id
              )
            """)
    List<SubscriptionEntity> findLatestForUsers(@Param("userIds") Set<UUID> userIds);

    @Query("""
            select case when count(subscription) > 0 then true else false end
            from SubscriptionEntity subscription
            where subscription.user.id = :userId
              and subscription.externalSubscriptionId is not null
              and (
                    subscription.status in :blockingStatuses
                 or (subscription.status = :cancelledStatus and subscription.endsAt > :now)
              )
            """)
    boolean existsCheckoutBlockingSubscription(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("blockingStatuses") Set<SubscriptionStatus> blockingStatuses,
            @Param("cancelledStatus") SubscriptionStatus cancelledStatus
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