package com.waypoint.backend.repository.admin;

import com.waypoint.backend.model.admin.AdminAuditEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AdminAuditEventRepository
        extends JpaRepository<AdminAuditEventEntity, UUID>, JpaSpecificationExecutor<AdminAuditEventEntity> {
}
