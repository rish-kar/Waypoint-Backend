package com.waypoint.backend.plan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<PlanEntity, PlanCode> {
}
