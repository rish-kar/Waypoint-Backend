package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FamilyAiPersistenceIntegrationTests {
    private final SpecialPremiumGrantRepository grantRepository;
    private final PlanRepository planRepository;

    @Autowired
    FamilyAiPersistenceIntegrationTests(
            SpecialPremiumGrantRepository grantRepository,
            PlanRepository planRepository
    ) {
        this.grantRepository = grantRepository;
        this.planRepository = planRepository;
    }

    @Test
    @Transactional
    void applicationContextLoadsFamilyAiSchemaAndLockingQueries() {
        assertThat(grantRepository.sumAiSpentMicrorupeesForPeriod("2099-01")).isNull();
        assertThat(planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)).isPresent();
    }
}
