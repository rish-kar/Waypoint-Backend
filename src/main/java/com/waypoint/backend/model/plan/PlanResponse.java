package com.waypoint.backend.model.plan;

public record PlanResponse(
        PlanCode code,
        String displayName,
        BillingInterval billingInterval,
        int price,
        String currency,
        boolean premium
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
                plan.isPremium()
        );
    }
}
