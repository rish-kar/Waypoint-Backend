package com.waypoint.backend.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    List<SubscriptionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);
}
