package com.waypoint.backend.repository.admin;

import com.waypoint.backend.model.admin.AdminAccountEntity;
import com.waypoint.backend.model.admin.AdminRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {
    Optional<AdminAccountEntity> findByUsernameIgnoreCase(String username);

    List<AdminAccountEntity> findAllByOrderByUsernameAsc();

    long countByRoleAndActiveTrue(AdminRole role);
}