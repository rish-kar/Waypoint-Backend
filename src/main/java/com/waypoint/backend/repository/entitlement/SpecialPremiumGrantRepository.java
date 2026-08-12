package com.waypoint.backend.repository.entitlement;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialPremiumGrantRepository extends JpaRepository<SpecialPremiumGrantEntity, UUID> {
    Optional<SpecialPremiumGrantEntity> findByUserId(UUID userId);

    List<SpecialPremiumGrantEntity> findByActiveTrueOrderByGrantedAtDesc();
}
