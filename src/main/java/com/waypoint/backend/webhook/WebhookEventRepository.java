package com.waypoint.backend.webhook;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID> {
    Optional<WebhookEventEntity> findByEventHash(String eventHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventEntity event where event.eventHash = :eventHash")
    Optional<WebhookEventEntity> findByEventHashForUpdate(@Param("eventHash") String eventHash);
}
