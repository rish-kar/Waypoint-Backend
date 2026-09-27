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
    void catalogueUsesCurrentUsdPricing() {
        var free = planRepository.findById(PlanCode.FREE).orElseThrow();
        var monthly = planRepository.findById(PlanCode.PREMIUM_MONTHLY).orElseThrow();
        var annual = planRepository.findById(PlanCode.PREMIUM_ANNUAL).orElseThrow();
        var special = planRepository.findById(PlanCode.PREMIUM_SPECIAL).orElseThrow();
        var admin = planRepository.findById(PlanCode.ADMIN).orElseThrow();

        assertThat(free.getPrice()).isZero();
        assertThat(free.getCurrency()).isEqualTo("USD");

        assertThat(monthly.getPrice()).isEqualTo(499);
        assertThat(monthly.getCurrency()).isEqualTo("USD");

        assertThat(annual.getPrice()).isEqualTo(3999);
        assertThat(annual.getCurrency()).isEqualTo("USD");

        assertThat(special.getPrice()).isZero();
        assertThat(special.getCurrency()).isEqualTo("USD");

        assertThat(admin.getPrice()).isZero();
        assertThat(admin.getCurrency()).isEqualTo("USD");
        assertThat(admin.getBillingInterval()).isEqualTo(BillingInterval.NONE);
        assertThat(admin.isPremium()).isTrue();
        assertThat(admin.isActive()).isFalse();
    }
}
