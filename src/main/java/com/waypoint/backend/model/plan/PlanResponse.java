package com.waypoint.backend.model.plan;

public record PlanResponse(
        PlanCode code,
        String displayName,
        BillingInterval billingInterval,
        int priceCents,
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
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.isPremium()
        );
    }

    public static PlanResponse from(PlanEntity plan, int priceCents, String currency) {
        if (plan == null) {
            return null;
        }
        return new PlanResponse(
                plan.getCode(),
                plan.getDisplayName(),
                plan.getBillingInterval(),
                priceCents,
                currency,
                plan.isPremium()
        );
    }
}