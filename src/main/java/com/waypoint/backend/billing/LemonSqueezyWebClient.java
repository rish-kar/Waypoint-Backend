package com.waypoint.backend.billing;

import com.waypoint.backend.common.ExternalServiceException;
import com.waypoint.backend.common.InvalidRequestException;
import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.user.UserEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
public class LemonSqueezyWebClient implements LemonSqueezyClient {
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    private final WebClient webClient;
    private final LemonSqueezyProperties properties;

    public LemonSqueezyWebClient(WebClient.Builder builder, LemonSqueezyProperties properties) {
        this.webClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public String createCheckout(UserEntity user, CheckoutPlan plan, String variantId) {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.storeId())) {
            throw new InvalidRequestException("Lemon Squeezy checkout is not configured");
        }
        Map<String, Object> body = Map.of(
                "data", Map.of(
                        "type", "checkouts",
                        "attributes", Map.of(
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
                    .block();
            String url = response == null ? null : response.path("data").path("attributes").path("url").asText(null);
            if (!StringUtils.hasText(url)) {
                throw new ExternalServiceException("Lemon Squeezy did not return a checkout URL");
            }
            return url;
        } catch (WebClientResponseException exception) {
            throw new ExternalServiceException("Unable to create Lemon Squeezy checkout");
        }
    }
}
