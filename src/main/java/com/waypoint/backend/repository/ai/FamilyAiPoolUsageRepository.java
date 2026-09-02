package com.waypoint.backend.repository.ai;

import com.waypoint.backend.model.ai.FamilyAiPoolUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface FamilyAiPoolUsageRepository extends JpaRepository<FamilyAiPoolUsageEntity, String> {
    @Modifying
    @Query(value = "insert into family_ai_pool_usage(period_key, spent_microrupees, updated_at) values (:periodKey, 0, now()) on conflict (period_key) do nothing", nativeQuery = true)
    void ensurePeriod(@Param("periodKey") String periodKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FamilyAiPoolUsageEntity p where p.periodKey = :periodKey")
    Optional<FamilyAiPoolUsageEntity> findForUpdate(@Param("periodKey") String periodKey);
}
