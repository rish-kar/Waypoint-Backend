package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.utilities.exception.ExternalServiceException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class LemonSqueezySubscriptionWebClient implements LemonSqueezySubscriptionClient {
    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int PAGE_SIZE = 100;

    private final WebClient webClient;
    private final LemonSqueezyProperties properties;

    public LemonSqueezySubscriptionWebClient(WebClient.Builder builder, LemonSqueezyProperties properties) {
        this.webClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public List<ProviderSubscriptionSnapshot> listSubscriptions() {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.storeId())) {
            throw new ExternalServiceException("Lemon Squeezy reconciliation is not configured");
        }

        List<ProviderSubscriptionSnapshot> subscriptions = new ArrayList<>();
        int page = 1;
        int lastPage;
        do {
            JsonNode response = fetchPage(page);
            JsonNode data = response.path("data");
            if (!data.isArray()) {
                throw new ExternalServiceException("Lemon Squeezy returned an invalid subscription list");
            }
            for (JsonNode subscription : data) {
                subscriptions.add(toSnapshot(subscription));
            }
            JsonNode pageMeta = response.path("meta").path("page");
            int snakeCaseLastPage = pageMeta.path("last_page").asInt(page);
            lastPage = pageMeta.path("lastPage").asInt(snakeCaseLastPage);
            page++;
        } while (page <= lastPage);

        return List.copyOf(subscriptions);
    }

    private JsonNode fetchPage(int page) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/subscriptions")
                            .queryParam("filter[store_id]", properties.storeId())
                            .queryParam("page[size]", PAGE_SIZE)
                            .queryParam("page[number]", page)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .accept(JSON_API)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
            if (response == null) {
                throw new ExternalServiceException("Lemon Squeezy returned an empty subscription response");
            }
            return response;
        } catch (WebClientResponseException | WebClientRequestException exception) {
            throw new ExternalServiceException("Unable to list Lemon Squeezy subscriptions");
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("Unable to list Lemon Squeezy subscriptions");
        }
    }

    private ProviderSubscriptionSnapshot toSnapshot(JsonNode node) {
        JsonNode attributes = node.path("attributes");
        String externalSubscriptionId = text(node, "id");
        Instant providerUpdatedAt = parseInstant(text(attributes, "updated_at"));
        if (!StringUtils.hasText(externalSubscriptionId) || providerUpdatedAt == null) {
            throw new ExternalServiceException("Lemon Squeezy returned an invalid subscription object");
        }
        return new ProviderSubscriptionSnapshot(
                externalSubscriptionId,
                text(attributes, "user_email"),
                text(attributes, "customer_id"),
                text(attributes, "product_id"),
                text(attributes, "variant_id"),
                text(attributes, "status"),
                parseInstant(text(attributes, "renews_at")),
                parseInstant(text(attributes, "ends_at")),
                providerUpdatedAt
        );
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value);
            } catch (Exception exception) {
                return null;
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
