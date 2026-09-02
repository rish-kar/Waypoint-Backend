package com.waypoint.backend.service.ai;

import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FamilyAiPersistenceIntegrationTests {
    private final SpecialPremiumGrantRepository grantRepository;

    FamilyAiPersistenceIntegrationTests(SpecialPremiumGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    @Test
    void applicationContextLoadsFamilyAiSchemaAndRepositoryQuery() {
        assertThat(grantRepository.sumAiSpentMicrorupeesForPeriod("2099-01")).isNull();
    }
}
