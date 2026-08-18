package com.waypoint.backend.model.billing;

public record ProviderPriceCatalog(
        int monthlyPriceCents,
        int annualPriceCents,
        String currency
) {
}