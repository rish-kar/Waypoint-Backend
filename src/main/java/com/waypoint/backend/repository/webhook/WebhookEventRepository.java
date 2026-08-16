package com.waypoint.backend.repository.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEventEntity, UUID>, JpaSpecificationExecutor<WebhookEventEntity> {
    Optional<WebhookEventEntity> findByEventHash(String eventHash);

    long countByProcessingStatus(ProcessingStatus processingStatus);

    List<WebhookEventEntity> findTop100ByProcessingStatusAndLastAttemptAtBeforeOrderByLastAttemptAtAsc(
            ProcessingStatus processingStatus,
            Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventEntity event where event.eventHash = :eventHash")
    Optional<WebhookEventEntity> findByEventHashForUpdate(@Param("eventHash") String eventHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventEntity event where event.id = :eventId")
    Optional<WebhookEventEntity> findByIdForUpdate(@Param("eventId") UUID eventId);
}