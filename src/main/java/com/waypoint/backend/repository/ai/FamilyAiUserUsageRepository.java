package com.waypoint.backend.repository.ai;

import com.waypoint.backend.model.ai.FamilyAiUserUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface FamilyAiUserUsageRepository extends JpaRepository<FamilyAiUserUsageEntity, UUID> {
    Optional<FamilyAiUserUsageEntity> findByUserIdAndPeriodKey(UUID userId, String periodKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from FamilyAiUserUsageEntity u where u.user.id = :userId and u.periodKey = :periodKey")
    Optional<FamilyAiUserUsageEntity> findForUpdate(
            @Param("userId") UUID userId,
            @Param("periodKey") String periodKey
    );
}
