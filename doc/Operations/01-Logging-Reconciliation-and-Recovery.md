# Logging, Reconciliation, Recovery and Observability

## Request logging

Application code uses SLF4J/Logback. Request infrastructure associates a safe incoming or generated `X-Request-ID` with MDC and responses so one request can be traced across controller/service/client logs.

Log request method, normalized path, response status and duration; avoid query strings/bodies when they can contain tokens or personal/provider data.

## Sensitive-data rules

Never log:

- Authorization bearer tokens;
- admin Basic credentials or TOTP codes/secrets;
- Google access tokens;
- Lemon Squeezy API/webhook secrets;
- raw webhook signatures;
- full webhook bodies unless an explicitly secured diagnostic workflow requires it;
- database passwords/URLs with embedded credentials.

Webhook payload persistence itself is subject to redaction/controlled admin inclusion.

## Health/availability

Actuator exposes liveness/readiness. Database health is part of readiness. Redis is operationally required for production distributed rate limiting even though JWT revocation can fall back to PostgreSQL, so infrastructure monitoring should separately alert on Redis failures/latency.

## Subscription reconciliation

Production enables Lemon Squeezy reconciliation with default initial delay 60 seconds and interval 15 minutes. This is a convergence mechanism for missed/delayed webhook/provider drift.

Operationally, investigate in this order:

1. webhook event receipt/signature/processing status;
2. provider event time/order;
3. local subscription external IDs/status/end/trial fields;
4. reconciliation logs/result;
5. effective entitlement.

Use controlled admin correction only when replay/reconciliation cannot safely reconstruct the desired state.

## JWT revocation cleanup

Expired revocation database rows are periodically removed. Redis revocation keys self-expire at JWT expiry. If Redis is lost, persisted revocation rows preserve correctness through fallback lookup.

## Webhook recovery

Webhook records store processing metadata/attempts so failures can be retried/diagnosed without inventing provider events. Prefer recovery paths that re-run validated domain processing over direct database edits.

## Metrics and alerts

At minimum monitor externally:

- request rate/5xx/latency;
- 401/403/429/413 spikes;
- login/provider-client failures;
- webhook signature/processing failures;
- reconciliation failures/drift;
- PostgreSQL pool saturation/slow queries;
- Redis errors/latency;
- readiness failures;
- container memory/restarts.

## Incident evidence

Capture request ID, endpoint, status, timestamp, user UUID/provider object IDs as appropriate, and redacted exception classification. Never attach secrets/tokens to tickets.
