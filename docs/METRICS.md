# Metrics and Prometheus

Waypoint exposes runtime and application metrics through Spring Boot Actuator and Micrometer's Prometheus registry.

## Endpoint

```text
GET /actuator/prometheus
Authorization: Basic Auth using ADMIN_ID and ADMIN_PASSWORD
```

Health, liveness and readiness remain public. Prometheus metrics use the same admin ID and password as the existing admin API. A Waypoint user JWT does not grant access to metrics.

Under the `prod` profile the metrics endpoint is HTTPS-only. Keep the admin credentials in the deployment/monitoring secret store and restrict network access to the scraper wherever possible.

The metrics endpoint intentionally does not require the rotating admin TOTP because an automated Prometheus scraper cannot supply a continuously rotating interactive code. `/api/v1/admin/**` keeps its existing production TOTP requirement.

## Included metrics

Micrometer/Spring Boot provide standard runtime metrics including:

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

The `area` tag is limited to `auth`, `ai`, `billing`, `webhook`, `admin`, `account`, `entitlement`, `subscription` and `other`. Metrics never use user IDs, email addresses, raw request URLs, request bodies, OAuth tokens or other user-controlled values as labels.

In Prometheus exposition format the custom names include:

```text
waypoint_api_requests_total
waypoint_api_errors_total
waypoint_api_request_duration_seconds_count
waypoint_api_request_duration_seconds_bucket
```

## Postman

Metrics checks are in the dedicated `08 - Metrics` folder of the existing Git-synced `Waypoint-Backend` collection. No separate metrics credential is required.

Set the existing `Waypoint Local` values:

```text
adminId = same value as ADMIN_ID
adminPassword = same value as ADMIN_PASSWORD
```

Run these requests in order:

1. `01 - Missing Admin Credentials` — expects `401`.
2. `02 - Invalid Admin Credentials` — expects `401`.
3. `03 - Generate Waypoint API Metric` — creates a safe custom Waypoint metric sample.
4. `04 - Prometheus` — expects `200` and verifies JVM, HTTP and Waypoint metrics.

## Prometheus scrape example

```yaml
scrape_configs:
  - job_name: waypoint-backend
    scheme: https
    metrics_path: /actuator/prometheus
    basic_auth:
      username: <ADMIN_ID>
      password: <ADMIN_PASSWORD>
    static_configs:
      - targets:
          - backend.example.com
```

Do not commit real admin credentials to Prometheus configuration stored in source control. Inject them from the monitoring platform's secret store.

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
