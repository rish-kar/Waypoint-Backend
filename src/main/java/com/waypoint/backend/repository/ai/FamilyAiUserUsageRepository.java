package com.waypoint.backend.repository.ai;

import com.waypoint.backend.model.ai.FamilyAiUserUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FamilyAiUserUsageRepository extends JpaRepository<FamilyAiUserUsageEntity, UUID> {
    Optional<FamilyAiUserUsageEntity> findByUserIdAndPeriodKey(UUID userId, String periodKey);
}
