package com.waypoint.backend.config.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyAiAccessPropertiesTests {
    @Test
    void rejectsMissingOrNonPositiveMonthlyBudget() {
        assertThatThrownBy(() -> properties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI_FAMILY_MONTHLY_BUDGET_RUPEES");

        assertThatThrownBy(() -> properties(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI_FAMILY_MONTHLY_BUDGET_RUPEES");
    }

    @Test
    void acceptsMonthlyBudgetProvidedByConfiguration() {
        FamilyAiAccessProperties properties = properties(5_000);
        assertThat(properties.monthlyBudgetRupees()).isEqualTo(5_000);
    }

    private FamilyAiAccessProperties properties(long monthlyBudgetRupees) {
        return new FamilyAiAccessProperties(
                monthlyBudgetRupees,
                5,
                25,
                new BigDecimal("100"),
                new BigDecimal("0.05"),
                new BigDecimal("0.40")
        );
    }
}
