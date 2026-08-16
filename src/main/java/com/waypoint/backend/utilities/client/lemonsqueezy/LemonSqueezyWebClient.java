package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.ProviderPriceCatalog;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.utilities.exception.ExternalServiceException;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class LemonSqueezyWebClient implements LemonSqueezyClient {
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final LemonSqueezyProperties properties;
    private final Duration requestTimeout;

    public LemonSqueezyWebClient(WebClient.Builder builder, LemonSqueezyProperties properties) {
        this(builder, properties, REQUEST_TIMEOUT);
    }

    LemonSqueezyWebClient(WebClient.Builder builder, LemonSqueezyProperties properties, Duration requestTimeout) {
        this.webClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String createCheckout(UserEntity user, CheckoutPlan plan, String variantId) {
        requireApiConfiguration();

        long enabledVariantId = parseVariantId(variantId);
        Map<String, Object> body = Map.of(
                "data", Map.of(
                        "type", "checkouts",
                        "attributes", Map.of(
                                "product_options", Map.of(
                                        "enabled_variants", List.of(enabledVariantId)
                                ),
                                "checkout_data", Map.of(
                                        "email", user.getEmail(),
                                        "custom", Map.of(
                                                "waypoint_user_id", user.getId().toString(),
                                                "waypoint_plan", plan.name()
                                        )
                                )
                        ),
                        "relationships", Map.of(
                                "store", Map.of("data", Map.of("type", "stores", "id", properties.storeId())),
                                "variant", Map.of("data", Map.of("type", "variants", "id", variantId))
                        )
                )
        );
        try {
            JsonNode response = webClient.post()
                    .uri("/checkouts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .accept(JSON_API)
                    .contentType(JSON_API)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(requestTimeout)
                    .block();
            String url = response == null ? null : response.path("data").path("attributes").path("url").asText(null);
            if (!StringUtils.hasText(url)) {
                throw new ExternalServiceException("Lemon Squeezy did not return a checkout URL");
            }
            return url;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("Unable to create Lemon Squeezy checkout");
        }
    }

    @Override
    public ProviderPriceCatalog fetchPriceCatalog(String monthlyVariantId, String annualVariantId) {
        requireApiConfiguration();
        parseVariantId(monthlyVariantId);
        parseVariantId(annualVariantId);

        try {
            Tuple3<Integer, Integer, String> catalog = Mono.zip(
                            fetchCurrentPrice(monthlyVariantId),
                            fetchCurrentPrice(annualVariantId),
                            fetchStoreCurrency()
                    )
                    .timeout(requestTimeout)
                    .block();
            if (catalog == null) {
                throw new ExternalServiceException("Lemon Squeezy did not return plan pricing");
            }
            return new ProviderPriceCatalog(catalog.getT1(), catalog.getT2(), catalog.getT3());
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("Unable to load Lemon Squeezy plan pricing");
        }
    }

    private Mono<Integer> fetchCurrentPrice(String variantId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/prices")
                        .queryParam("filter[variant_id]", variantId)
                        .queryParam("page[size]", 1)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .accept(JSON_API)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode data = response.path("data");
                    if (!data.isArray() || data.isEmpty()) {
                        throw new ExternalServiceException("Lemon Squeezy price is missing for variant " + variantId);
                    }
                    int unitPrice = data.get(0).path("attributes").path("unit_price").asInt(-1);
                    if (unitPrice < 0) {
                        throw new ExternalServiceException("Lemon Squeezy price is invalid for variant " + variantId);
                    }
                    return unitPrice;
                });
    }

    private Mono<String> fetchStoreCurrency() {
        return webClient.get()
                .uri("/stores/{storeId}", properties.storeId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .accept(JSON_API)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String currency = response.path("data").path("attributes").path("currency").asText(null);
                    if (!StringUtils.hasText(currency)) {
                        throw new ExternalServiceException("Lemon Squeezy store currency is missing");
                    }
                    return currency.trim().toUpperCase(java.util.Locale.ROOT);
                });
    }

    private void requireApiConfiguration() {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.storeId())) {
            throw new InvalidRequestException("Lemon Squeezy checkout is not configured");
        }
    }

    private long parseVariantId(String variantId) {
        try {
            long parsed = Long.parseLong(variantId);
            if (parsed <= 0) {
                throw new NumberFormatException("Variant ID must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ExternalServiceException("Lemon Squeezy variant configuration is invalid");
        }
    }
}