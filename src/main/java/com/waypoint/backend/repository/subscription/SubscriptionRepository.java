package com.waypoint.backend.repository.subscription;

import com.waypoint.backend.model.subscription.SubscriptionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository
        extends JpaRepository<SubscriptionEntity, UUID>, JpaSpecificationExecutor<SubscriptionEntity> {
    List<SubscriptionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);
}
