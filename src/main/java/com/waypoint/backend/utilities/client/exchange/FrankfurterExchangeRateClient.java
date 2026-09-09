package com.waypoint.backend.utilities.client.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FrankfurterExchangeRateClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrankfurterExchangeRateClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final WebClient webClient;
    private final String apiBaseUrl;
    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();

    public FrankfurterExchangeRateClient(
            WebClient.Builder webClientBuilder,
            @Value("${exchange-rate.api-base-url:https://api.frankfurter.dev}") String apiBaseUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.apiBaseUrl = normalizeBaseUrl(apiBaseUrl);
    }

    public BigDecimal rate(String baseCurrency, String quoteCurrency) {
        String base = normalizeCurrency(baseCurrency);
        String quote = normalizeCurrency(quoteCurrency);
        if (base == null || quote == null) {
            return null;
        }
        if (base.equals(quote)) {
            return BigDecimal.ONE;
        }

        String key = base + ':' + quote;
        CachedRate cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.rate();
        }

        try {
            JsonNode payload = webClient.get()
                    .uri(apiBaseUrl + "/v2/rate/" + base + "/" + quote)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
            JsonNode rateNode = payload == null ? null : payload.get("rate");
            BigDecimal rate = rateNode == null || rateNode.isNull() ? null : rateNode.decimalValue();
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalStateException("Exchange-rate response did not contain a positive rate");
            }
            cache.put(key, new CachedRate(rate, now.plus(CACHE_TTL)));
            return rate;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("event", "exchange_rate_lookup_failed")
                    .addKeyValue("base_currency", base)
                    .addKeyValue("quote_currency", quote)
                    .log("Localized pricing rate lookup failed; falling back to base currency");
            return cached == null ? null : cached.rate();
        }
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://api.frankfurter.dev";
        return normalized.replaceAll("/+$", "");
    }

    private static String normalizeCurrency(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    private record CachedRate(BigDecimal rate, Instant expiresAt) {
    }
}
