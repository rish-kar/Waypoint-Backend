# Request Lifecycle and Layers

## Common inbound lifecycle

A normal request passes through servlet infrastructure before reaching a controller:

```text
HTTP request
  -> request/body safety filters
  -> request ID / logging infrastructure
  -> rate-limit filter
  -> selected Spring Security chain
  -> JWT or admin authentication filters
  -> controller validation
  -> application service
  -> repository / provider client
  -> response / global exception mapping
```

`RequestBodySizeLimitFilter` runs at high precedence for `/api/` POST, PUT and PATCH requests. It checks `Content-Length` and also reads at most 1 MiB + 1 byte, so chunked/incorrectly-declared oversized payloads still receive HTTP 413.

## Security-chain selection

`SecurityConfig` defines two ordered chains.

### Admin chain — order 1

Matches `/api/v1/admin/**`. It uses stateless HTTP Basic authentication backed by `AdminAccountService`, role rules, distributed rate limiting, and in production an `AdminTotpFilter`. Normal Waypoint JWTs do not authenticate this chain.

### Standard API chain — order 2

Uses stateless bearer JWT authentication. Public exceptions are Google auth, the Lemon Squeezy webhook, health endpoints and OPTIONS requests. All other requests require authentication.

## Controller layer

Controllers should translate HTTP concerns into validated service calls. Request models use Bean Validation. Business decisions belong in services rather than controller branches.

Current controller groups include auth, account, billing, subscriptions, entitlements, webhooks, admin operations and admin-account management.

## Service layer

Services orchestrate business workflows such as:

- Google identity resolution and local user login;
- JWT issue/revoke behavior;
- checkout coordination and provider calls;
- subscription state updates and reconciliation;
- effective entitlement selection;
- Premium Special grant/revoke precedence;
- webhook verification/processing/recovery;
- admin query/mutation auditing.

Use Spring transactions around workflows that must update related records atomically. Avoid provider network calls inside large transactions where practical.

## Repository layer

Spring Data repositories own persistence queries. Hot/filterable admin paths have dedicated indexes/migrations. Repositories should not contain provider-specific HTTP behavior.

## Provider clients

Outbound Google and Lemon Squeezy communication uses WebClient-based clients. Keep provider JSON/HTTP details behind those clients and normalize into domain/service models before persistence.

## Error contract

Security failures and application errors return JSON error structures rather than HTML login/error pages. Server configuration disables exception/message/stacktrace leakage in default HTTP error responses.

## Request ID and logging

Requests accept a safe incoming `X-Request-ID` or receive a generated identifier. The ID is returned and put into MDC so logs can be correlated without logging request bodies, tokens or query-string secrets.

## Background work

Scheduled jobs such as subscription reconciliation and JWT revocation cleanup run outside an inbound request. Their state changes must obey the same database invariants and logging/audit expectations as request-driven workflows.
