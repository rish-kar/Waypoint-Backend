package com.waypoint.backend.repository.billing;

import com.waypoint.backend.model.billing.BillingCheckoutSessionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingCheckoutSessionRepository extends JpaRepository<BillingCheckoutSessionEntity, UUID> {
}
