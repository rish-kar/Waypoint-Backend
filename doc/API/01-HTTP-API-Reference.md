# HTTP API Reference

Base prefix: `/api/v1` unless noted. Normal protected endpoints use a Waypoint bearer JWT. Admin endpoints use the separate admin security chain.

## Public endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/google` | Validate Google access token, resolve/create user and issue Waypoint JWT. |
| `POST` | `/api/v1/webhooks/lemonsqueezy` | Receive signed Lemon Squeezy events. |
| `GET` | `/actuator/health` | Overall health. |
| `GET` | `/actuator/health/liveness` | Liveness probe. |
| `GET` | `/actuator/health/readiness` | Readiness including database connectivity. |

## Authenticated user endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/account` | User profile plus effective account/entitlement view. |
| `GET` | `/api/v1/billing/plans` | Available customer-facing billing plans. |
| `POST` | `/api/v1/billing/checkout` | Create/coordinate hosted checkout for requested `CheckoutPlan`. |
| `GET` | `/api/v1/billing/status` | Stored billing/subscription state. |
| `GET` | `/api/v1/subscriptions/current` | Current subscription projection. |
| `GET` | `/api/v1/entitlements` | Effective entitlement/features. |
| `GET` | `/api/v1/entitlements/features/{feature}` | Check one feature. |

Authorization uses the authenticated user UUID from the JWT security context; callers do not select arbitrary user IDs for these endpoints.

## Admin read/mutation endpoints

The admin controller exposes:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/admin/overview` | Aggregate operational/business counts. |
| `GET` | `/api/v1/admin/users` | Paged/filterable users; `email=` remains a compatibility exact lookup form. |
| `GET` | `/api/v1/admin/users/by-email` | Exact email lookup. |
| `GET` | `/api/v1/admin/users/{userId}` | Inspect one user/effective plan. |
| `GET` | `/api/v1/admin/subscriptions` | Paged/filterable subscription records. |
| `GET` | `/api/v1/admin/subscriptions/{subscriptionId}` | Inspect one subscription. |
| `PATCH` | `/api/v1/admin/subscriptions/{subscriptionId}` | Controlled subscription correction. |
| `PUT` | `/api/v1/admin/users/{userId}/premium-special` | Grant/replace Premium Special. |
| `DELETE` | `/api/v1/admin/users/{userId}/premium-special` | Revoke Premium Special. |
| `GET` | `/api/v1/admin/premium-special` | Active Premium Special summary. |
| `GET` | `/api/v1/admin/special-grants` | Paged/filterable special grants. |
| `GET` | `/api/v1/admin/special-grants/{grantId}` | Inspect a grant. |
| `GET` | `/api/v1/admin/webhook-events` | Paged/filterable webhook processing records. |
| `GET` | `/api/v1/admin/webhook-events/{eventId}` | Inspect a webhook event. |
| `PATCH` | `/api/v1/admin/webhook-events/{eventId}` | Controlled processing-metadata correction. |
| `GET` | `/api/v1/admin/plans` | Local plan catalogue. |
| `GET` | `/api/v1/admin/audit-events` | Paged/filterable admin mutation audit trail. |

User/subscription/grant/webhook/audit list endpoints support combinations of filters, paging, sorting and direction as defined in `AdminController`.

## Admin-account endpoints

These are under `/api/v1/admin/accounts` and are restricted to `SUPER_ADMIN` by the security chain:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/admin/accounts` | List administrator accounts. |
| `POST` | `/api/v1/admin/accounts` | Create an administrator account. |
| `PATCH` | `/api/v1/admin/accounts/{accountId}` | Update an administrator account. |

All POST/PUT/PATCH admin operations are `SUPER_ADMIN`-only. Other admin reads allow `ADMIN` or `SUPER_ADMIN`, except the account family which always requires `SUPER_ADMIN`.

## Headers

Normal API authentication:

```text
Authorization: Bearer <waypoint-jwt>
```

Admin authentication:

```text
Authorization: Basic <credentials>
X-Admin-TOTP: <one-time-code>     # required by production admin filter
```

Webhook authentication:

```text
X-Signature: <HMAC-SHA256 signature>
```

`X-Request-ID` may be supplied for correlation if it passes request-ID safety rules; the server exposes the effective request ID in responses.

## Limits

- API POST/PUT/PATCH body: maximum 1 MiB.
- Production distributed per-IP one-minute rate buckets: Google auth 30, admin 120, webhook 120, default 600.

## Error handling

Clients should use HTTP status plus the JSON error `code`/`message`; do not parse Spring exception text. Security failures are emitted as JSON. Rate limiting returns 429 with `Retry-After: 60`. Oversized bodies return 413 with code `PAYLOAD_TOO_LARGE`.
