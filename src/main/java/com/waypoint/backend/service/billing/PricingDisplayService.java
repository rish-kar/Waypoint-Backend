package com.waypoint.backend.service.billing;

import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.utilities.client.exchange.FrankfurterExchangeRateClient;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class PricingDisplayService {
    private final FrankfurterExchangeRateClient exchangeRateClient;

    public PricingDisplayService(FrankfurterExchangeRateClient exchangeRateClient) {
        this.exchangeRateClient = exchangeRateClient;
    }

    public PlanResponse localize(PlanResponse plan, String providerLocale, String acceptLanguage) {
        if (plan == null) {
            return null;
        }

        String localeTag = resolveLocale(providerLocale, acceptLanguage);
        String targetCurrency = currencyForLocale(localeTag);
        String baseCurrency = normalizeCurrency(plan.currency());
        BigDecimal basePrice = BigDecimal.valueOf(plan.price());

        if (baseCurrency == null || targetCurrency == null || targetCurrency.equals(baseCurrency) || plan.price() <= 0) {
            return plan.withDisplayPrice(basePrice, baseCurrency == null ? plan.currency() : baseCurrency, localeTag, false);
        }

        BigDecimal rate = exchangeRateClient.rate(baseCurrency, targetCurrency);
        if (rate == null) {
            return plan.withDisplayPrice(basePrice, baseCurrency, localeTag, false);
        }

        int fractionDigits;
        try {
            fractionDigits = Currency.getInstance(targetCurrency).getDefaultFractionDigits();
        } catch (IllegalArgumentException exception) {
            fractionDigits = 2;
        }
        if (fractionDigits < 0) {
            fractionDigits = 2;
        }

        BigDecimal localized = basePrice.multiply(rate).setScale(fractionDigits, RoundingMode.HALF_UP);
        return plan.withDisplayPrice(localized, targetCurrency, localeTag, true);
    }

    public String resolveLocale(String providerLocale, String acceptLanguage) {
        String provider = normalizeLocale(providerLocale);
        String accepted = firstAcceptedLocale(acceptLanguage);

        if (hasRegion(provider)) {
            return provider;
        }
        if (hasRegion(accepted)) {
            if (provider == null || sameLanguage(provider, accepted)) {
                return accepted;
            }
        }
        return provider != null ? provider : accepted;
    }

    private String firstAcceptedLocale(String acceptLanguage) {
        if (!StringUtils.hasText(acceptLanguage)) {
            return null;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            for (Locale.LanguageRange range : ranges) {
                if (!"*".equals(range.getRange())) {
                    String normalized = normalizeLocale(range.getRange());
                    if (normalized != null) {
                        return normalized;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            return normalizeLocale(acceptLanguage.split(",", 2)[0]);
        }
        return null;
    }

    private String currencyForLocale(String localeTag) {
        if (!hasRegion(localeTag)) {
            return null;
        }
        try {
            return Currency.getInstance(Locale.forLanguageTag(localeTag)).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameLanguage(String left, String right) {
        Locale leftLocale = Locale.forLanguageTag(left);
        Locale rightLocale = Locale.forLanguageTag(right);
        return StringUtils.hasText(leftLocale.getLanguage())
                && leftLocale.getLanguage().equalsIgnoreCase(rightLocale.getLanguage());
    }

    private boolean hasRegion(String localeTag) {
        if (!StringUtils.hasText(localeTag)) {
            return false;
        }
        return StringUtils.hasText(Locale.forLanguageTag(localeTag).getCountry());
    }

    private String normalizeLocale(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(value.trim().replace('_', '-'));
        if (!StringUtils.hasText(locale.getLanguage())) {
            return null;
        }
        String tag = locale.toLanguageTag();
        return tag.length() <= 35 ? tag : null;
    }

    private String normalizeCurrency(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }
}
