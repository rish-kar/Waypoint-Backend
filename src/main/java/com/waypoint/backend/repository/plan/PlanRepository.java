package com.waypoint.backend.repository.plan;

import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<PlanEntity, PlanCode> {
    List<PlanEntity> findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval billingInterval);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from PlanEntity plan where plan.code = :code")
    Optional<PlanEntity> findByCodeForUpdate(@Param("code") PlanCode code);
}
