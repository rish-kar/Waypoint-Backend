package com.waypoint.backend.repository.plan;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<PlanEntity, PlanCode> {
    List<PlanEntity> findByActiveTrueAndPremiumTrueOrderByPriceCentsAsc();
}
