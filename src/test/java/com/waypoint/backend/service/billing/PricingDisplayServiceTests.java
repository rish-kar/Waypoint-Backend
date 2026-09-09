package com.waypoint.backend.service.billing;

import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.utilities.client.exchange.FrankfurterExchangeRateClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingDisplayServiceTests {
    private FrankfurterExchangeRateClient exchangeRateClient;
    private PricingDisplayService service;

    @BeforeEach
    void setUp() {
        exchangeRateClient = mock(FrankfurterExchangeRateClient.class);
        service = new PricingDisplayService(exchangeRateClient);
    }

    @Test
    void convertsInrForCountryQualifiedProviderLocaleWithoutChangingBasePrice() {
        when(exchangeRateClient.rate("INR", "USD")).thenReturn(new BigDecimal("0.0125"));

        PlanResponse result = service.localize(monthly(), "en-US", "en-IN,en;q=0.9");

        assertThat(result.price()).isEqualTo(399);
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.displayPrice()).isEqualByComparingTo("4.99");
        assertThat(result.displayCurrency()).isEqualTo("USD");
        assertThat(result.displayLocale()).isEqualTo("en-US");
        assertThat(result.displayPriceApproximate()).isTrue();
    }

    @Test
    void convertsAnnualPlanWithoutChangingCanonicalAnnualPrice() {
        when(exchangeRateClient.rate("INR", "USD")).thenReturn(new BigDecimal("0.012"));

        PlanResponse result = service.localize(annual(), "en-US", null);

        assertThat(result.price()).isEqualTo(3500);
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.displayPrice()).isEqualByComparingTo("42.00");
        assertThat(result.displayCurrency()).isEqualTo("USD");
        assertThat(result.displayPriceApproximate()).isTrue();
    }

    @Test
    void prefersProviderRegionAndSkipsRateLookupForInrLocale() {
        PlanResponse result = service.localize(monthly(), "en-IN", "en-US,en;q=0.9");

        assertThat(result.displayPrice()).isEqualByComparingTo("399");
        assertThat(result.displayCurrency()).isEqualTo("INR");
        assertThat(result.displayLocale()).isEqualTo("en-IN");
        assertThat(result.displayPriceApproximate()).isFalse();
        verify(exchangeRateClient, never()).rate("INR", "INR");
    }

    @Test
    void usesBrowserRegionWhenProviderOnlySuppliesLanguage() {
        assertThat(service.resolveLocale("en", "en-US,en;q=0.9")).isEqualTo("en-US");
    }

    @Test
    void fallsBackToActualInrPriceWhenExchangeProviderIsUnavailable() {
        when(exchangeRateClient.rate("INR", "USD")).thenReturn(null);

        PlanResponse result = service.localize(monthly(), "en-US", null);

        assertThat(result.displayPrice()).isEqualByComparingTo("399");
        assertThat(result.displayCurrency()).isEqualTo("INR");
        assertThat(result.displayPriceApproximate()).isFalse();
        verify(exchangeRateClient).rate("INR", "USD");
    }

    private PlanResponse monthly() {
        return plan(PlanCode.PREMIUM_MONTHLY, "Premium Monthly", BillingInterval.MONTHLY, 399);
    }

    private PlanResponse annual() {
        return plan(PlanCode.PREMIUM_ANNUAL, "Premium Annual", BillingInterval.ANNUAL, 3500);
    }

    private PlanResponse plan(PlanCode code, String displayName, BillingInterval interval, int price) {
        PlanEntity plan = new PlanEntity();
        plan.setCode(code);
        plan.setDisplayName(displayName);
        plan.setBillingInterval(interval);
        plan.setPrice(price);
        plan.setCurrency("INR");
        plan.setPremium(true);
        return PlanResponse.from(plan);
    }
}
