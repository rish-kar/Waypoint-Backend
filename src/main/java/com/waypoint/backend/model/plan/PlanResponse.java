package com.waypoint.backend.model.plan;

import java.math.BigDecimal;

public record PlanResponse(
        PlanCode code,
        String displayName,
        BillingInterval billingInterval,
        int price,
        String currency,
        boolean premium,
        BigDecimal displayPrice,
        String displayCurrency,
        String displayLocale,
        boolean displayPriceApproximate
) {
    public static PlanResponse from(PlanEntity plan) {
        if (plan == null) {
            return null;
        }
        return new PlanResponse(
                plan.getCode(),
                plan.getDisplayName(),
                plan.getBillingInterval(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.isPremium(),
                BigDecimal.valueOf(plan.getPrice()),
                plan.getCurrency(),
                null,
                false
        );
    }

    public PlanResponse withDisplayPrice(
            BigDecimal localizedPrice,
            String localizedCurrency,
            String localizedLocale,
            boolean approximate
    ) {
        return new PlanResponse(
                code,
                displayName,
                billingInterval,
                price,
                currency,
                premium,
                localizedPrice,
                localizedCurrency,
                localizedLocale,
                approximate
        );
    }
}
