package com.waypoint.backend.service.plan;

import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.repository.plan.PlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlanCatalogueMigrationIntegrationTests {
    private final PlanRepository planRepository;

    @Autowired
    PlanCatalogueMigrationIntegrationTests(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Test
    void catalogueUsesCurrentInrPricing() {
        var free = planRepository.findById(PlanCode.FREE).orElseThrow();
        var monthly = planRepository.findById(PlanCode.PREMIUM_MONTHLY).orElseThrow();
        var annual = planRepository.findById(PlanCode.PREMIUM_ANNUAL).orElseThrow();
        var special = planRepository.findById(PlanCode.PREMIUM_SPECIAL).orElseThrow();
        var admin = planRepository.findById(PlanCode.ADMIN).orElseThrow();

        assertThat(free.getPriceCents()).isZero();
        assertThat(free.getCurrency()).isEqualTo("INR");

        assertThat(monthly.getPriceCents()).isEqualTo(39900);
        assertThat(monthly.getCurrency()).isEqualTo("INR");

        assertThat(annual.getPriceCents()).isEqualTo(350000);
        assertThat(annual.getCurrency()).isEqualTo("INR");

        assertThat(special.getPriceCents()).isZero();
        assertThat(special.getCurrency()).isEqualTo("INR");

        assertThat(admin.getPriceCents()).isZero();
        assertThat(admin.getCurrency()).isEqualTo("INR");
        assertThat(admin.getBillingInterval()).isEqualTo(BillingInterval.NONE);
        assertThat(admin.isPremium()).isTrue();
        assertThat(admin.isActive()).isFalse();
    }
}
