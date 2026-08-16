package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.utilities.exception.ExternalServiceException;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
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
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.storeId())) {
            throw new InvalidRequestException("Lemon Squeezy checkout is not configured");
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
