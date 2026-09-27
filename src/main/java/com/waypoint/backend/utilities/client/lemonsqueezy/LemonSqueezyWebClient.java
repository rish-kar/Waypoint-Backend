package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.utilities.exception.ExternalServiceException;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LemonSqueezyWebClient implements LemonSqueezyClient {
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int CHECKOUT_PAGE_SIZE = 100;

    private final WebClient webClient;
    private final LemonSqueezyProperties properties;
    private final Duration requestTimeout;

    @Autowired
    public LemonSqueezyWebClient(WebClient.Builder builder, LemonSqueezyProperties properties) {
        this(builder, properties, REQUEST_TIMEOUT);
    }

    LemonSqueezyWebClient(WebClient.Builder builder, LemonSqueezyProperties properties, Duration requestTimeout) {
        this.webClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void validateCheckoutConfiguration(String variantId, CheckoutPlan plan) {
        requireApiConfiguration();
        long parsedVariantId = parseVariantId(variantId);
        if (plan == null) throw new InvalidRequestException("plan is required");

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/prices")
                            .queryParam("filter[variant_id]", parsedVariantId)
                            .queryParam("page[size]", 1)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .accept(JSON_API)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(requestTimeout)
                    .block();

            JsonNode price = response == null ? null : response.path("data").path(0).path("attributes");
            if (price == null || price.isMissingNode()) {
                throw new InvalidRequestException("Lemon Squeezy pricing is unavailable for the selected plan");
            }

            String expectedRenewalUnit = plan == CheckoutPlan.MONTHLY ? "month" : "year";
            int expectedUnitPrice = plan == CheckoutPlan.MONTHLY ? 499 : 3999;
            int unitPrice = price.path("unit_price").asInt(-1);
            String renewalUnit = price.path("renewal_interval_unit").asText("");
            int renewalQuantity = price.path("renewal_interval_quantity").asInt(0);
            String trialUnit = price.path("trial_interval_unit").asText("");
            int trialQuantity = price.path("trial_interval_quantity").asInt(0);

            if (unitPrice != expectedUnitPrice) {
                throw new InvalidRequestException("Lemon Squeezy variant price does not match the selected Waypoint plan");
            }
            if (!expectedRenewalUnit.equalsIgnoreCase(renewalUnit) || renewalQuantity != 1) {
                throw new InvalidRequestException("Lemon Squeezy variant does not match the selected Waypoint billing cycle");
            }
            if (!"day".equalsIgnoreCase(trialUnit) || trialQuantity != 3) {
                throw new InvalidRequestException("Lemon Squeezy variant must be configured with a 3-day free trial");
            }
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("Unable to verify Lemon Squeezy checkout configuration", exception);
        }
    }

    @Override
    public String createCheckout(UserEntity user, CheckoutPlan plan, String variantId) {
        return createCheckout(user, plan, variantId, UUID.randomUUID());
    }

    @Override
    public String createCheckout(UserEntity user, CheckoutPlan plan, String variantId, UUID intentId) {
        requireApiConfiguration();
        if (intentId == null) {
            throw new InvalidRequestException("Checkout intent is required");
        }

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
                                                "waypoint_plan", plan.name(),
                                                "waypoint_checkout_intent", intentId.toString()
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
            throw new ExternalServiceException("Unable to create Lemon Squeezy checkout", exception);
        }
    }

    @Override
    public Optional<String> findCheckoutByIntent(String variantId, UUID intentId) {
        requireApiConfiguration();
        if (intentId == null) {
            return Optional.empty();
        }
        parseVariantId(variantId);
        try {
            String expectedIntent = intentId.toString();
            int pageNumber = 1;
            while (true) {
                JsonNode response = fetchCheckoutPage(variantId, pageNumber);
                JsonNode data = response.path("data");
                if (!data.isArray()) {
                    return Optional.empty();
                }
                for (JsonNode checkout : data) {
                    JsonNode attributes = checkout.path("attributes");
                    String checkoutIntent = attributes.path("checkout_data")
                            .path("custom")
                            .path("waypoint_checkout_intent")
                            .asText(null);
                    if (!expectedIntent.equals(checkoutIntent)) {
                        continue;
                    }
                    String url = attributes.path("url").asText(null);
                    if (StringUtils.hasText(url)) {
                        return Optional.of(url);
                    }
                }

                int lastPage = response.path("meta").path("page").path("lastPage").asInt(pageNumber);
                if (pageNumber >= lastPage || data.isEmpty()) {
                    return Optional.empty();
                }
                pageNumber++;
            }
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("Unable to recover Lemon Squeezy checkout", exception);
        }
    }

    private JsonNode fetchCheckoutPage(String variantId, int pageNumber) {
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/checkouts")
                        .queryParam("filter[variant_id]", variantId)
                        .queryParam("page[size]", CHECKOUT_PAGE_SIZE)
                        .queryParam("page[number]", pageNumber)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .accept(JSON_API)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(requestTimeout)
                .block();
        if (response == null) {
            throw new ExternalServiceException("Lemon Squeezy did not return checkout recovery data");
        }
        return response;
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
            throw new ExternalServiceException("Lemon Squeezy variant configuration is invalid", exception);
        }
    }
}
