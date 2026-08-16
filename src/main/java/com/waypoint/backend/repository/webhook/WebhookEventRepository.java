package com.waypoint.backend.repository.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;
import com.waypoint.backend.model.webhook.WebhookEventEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEventEntity, UUID>, JpaSpecificationExecutor<WebhookEventEntity> {
    Optional<WebhookEventEntity> findByEventHash(String eventHash);

    long countByProcessingStatus(ProcessingStatus processingStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventEntity event where event.eventHash = :eventHash")
    Optional<WebhookEventEntity> findByEventHashForUpdate(@Param("eventHash") String eventHash);
}
