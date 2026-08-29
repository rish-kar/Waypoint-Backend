# Metrics and Prometheus

Waypoint exposes production metrics through Spring Boot Actuator and Micrometer's Prometheus registry.

## Endpoint

```text
GET /actuator/prometheus
Authorization: Bearer <MONITORING_METRICS_TOKEN>
```

Health, liveness and readiness endpoints remain public. Prometheus metrics use a dedicated monitoring token and do not accept Waypoint user JWTs or admin credentials.

Production requires `MONITORING_METRICS_TOKEN` to be a non-placeholder value of at least 32 characters and the endpoint is HTTPS-only under the `prod` profile. Keep the token in the deployment platform's secret manager and restrict network access to the scraper wherever possible.

## Included metrics

Micrometer/Spring Boot provide the standard runtime metrics, including:

- JVM memory, GC, thread and class-loading metrics
- process CPU and uptime metrics
- system CPU/load metrics
- HTTP server request count and duration metrics
- HikariCP connection-pool metrics
- Spring/DataSource observations made available by the active runtime

Waypoint also exposes bounded, privacy-safe application metrics:

- `waypoint.api.requests` — request count by `area`, HTTP `method` and coarse `outcome`
- `waypoint.api.errors` — 4xx/5xx request count using the same bounded tags
- `waypoint.api.request.duration` — request duration histogram using the same bounded tags

The `area` tag is limited to `auth`, `ai`, `billing`, `webhook`, `admin`, `account`, `entitlement`, `subscription` and `other`. Metrics never use user IDs, email addresses, request URLs, request bodies, OAuth tokens or other user-controlled values as labels.

In Prometheus exposition format the custom names become, for example:

```text
waypoint_api_requests_total
waypoint_api_errors_total
waypoint_api_request_duration_seconds_count
waypoint_api_request_duration_seconds_bucket
```

## Prometheus scrape example

```yaml
scrape_configs:
  - job_name: waypoint-backend
    scheme: https
    metrics_path: /actuator/prometheus
    authorization:
      type: Bearer
      credentials: <MONITORING_METRICS_TOKEN>
    static_configs:
      - targets:
          - backend.example.com
```

Do not commit the real monitoring token to Prometheus configuration stored in source control. Inject it from the monitoring platform's secret store.

## Postman verification

Import both:

- `postman/Waypoint-Metrics.postman_collection.json`
- `postman/Waypoint-Local.postman_environment.json`

Set the Postman environment variable:

```text
monitoringMetricsToken = same value as MONITORING_METRICS_TOKEN
```

Run the focused collection in order:

1. `01 - Metrics - Missing Token` verifies the endpoint rejects an unauthenticated scrape with `401`.
2. `02 - Metrics - Invalid Token` verifies a wrong monitoring bearer token is rejected with `401`.
3. `03 - Generate Waypoint API Metric` calls the public `/api/v1/ai/models` endpoint to create a deterministic custom metric sample without invoking an external AI provider.
4. `04 - Metrics - Prometheus` authenticates with `monitoringMetricsToken` and verifies Prometheus output contains JVM, HTTP-server and Waypoint custom metrics.

The Git-synced source is under `postman/collections/Waypoint-Metrics/`. `scripts/sync_postman_collection.py` generates both the main backend collection and the focused metrics collection. CI fails if either generated JSON export is out of sync with its YAML source.

## Useful queries

5xx rate by Waypoint area:

```promql
sum by (area) (rate(waypoint_api_errors_total{outcome="server_error"}[5m]))
```

Request rate by area:

```promql
sum by (area) (rate(waypoint_api_requests_total[5m]))
```

P95 Waypoint API latency by area:

```promql
histogram_quantile(
  0.95,
  sum by (le, area) (rate(waypoint_api_request_duration_seconds_bucket[5m]))
)
```

Overall Spring HTTP 5xx rate can also be derived from `http_server_requests_seconds_count`.
