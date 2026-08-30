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

## Postman

Metrics checks are part of the existing `Waypoint Backend API` collection under `00 - Health and Configuration`; no separate metrics collection is required.

The existing `Waypoint Local` environment includes:

```text
monitoringMetricsToken = waypoint-local-metrics-token-change-before-production
```

That value matches the backend's local default. If `MONITORING_METRICS_TOKEN` is overridden when starting the backend, set `monitoringMetricsToken` to the same value in the existing Postman environment.

Run these requests in order:

1. `Metrics - Missing Token` — expects `401`.
2. `Metrics - Invalid Token` — expects `401`.
3. `Generate Waypoint API Metric` — creates a safe custom Waypoint metric sample.
4. `Metrics - Prometheus` — expects `200` and verifies JVM, HTTP and Waypoint metrics.

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
