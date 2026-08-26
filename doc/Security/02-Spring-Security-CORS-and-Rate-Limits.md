# Spring Security, CORS, Rate Limits and Request Limits

## Two security chains

`SecurityConfig` creates ordered stateless chains.

### Admin chain

`@Order(1)` matches `/api/v1/admin/**`. It disables form login/logout/request cache, uses HTTP Basic backed by `AdminAccountService`, applies role authorization and rate limiting, and requires HTTPS plus `AdminTotpFilter` under the production profile.

Authorization:

- `/api/v1/admin/accounts/**`: `SUPER_ADMIN`.
- all admin POST/PUT/PATCH/DELETE: `SUPER_ADMIN`.
- other admin reads: `ADMIN` or `SUPER_ADMIN`.

### Standard chain

`@Order(2)` is a stateless bearer-token API. Public paths are OPTIONS, Google auth, Lemon Squeezy webhook and Actuator health. Everything else requires JWT authentication. Production requires a secure channel.

## CORS

Configured origins come from `CORS_ALLOWED_ORIGINS`; use explicit origins only. Allowed methods are GET, POST, PUT, PATCH, DELETE and OPTIONS. Allowed headers include `Authorization`, `Content-Type`, `X-Signature`, `X-Request-ID` and `X-Admin-TOTP`. `X-Request-ID` is exposed to clients and credentials are allowed.

Production startup rules should allow only HTTPS and required `chrome-extension://` origins; wildcard origins are incompatible with a credentialed security posture.

## Distributed rate limiting

`DistributedRateLimiter` uses Redis with an atomic Lua `INCR` + first-write `PEXPIRE` in a one-minute window. Keys are prefixed `waypoint:rate:` and include remote address plus route bucket.

`RequestRateLimitFilter` limits per minute:

| Bucket | Limit |
| --- | ---: |
| Google auth | 30 |
| Admin | 120 |
| Lemon Squeezy webhook | 120 |
| Default API | 600 |

When `security.distributed-state-enabled=false`, the limiter allows requests (development behavior). Production sets it to `true`. If Redis throws while distributed limiting is enabled, the limiter fails closed and the request is denied rather than silently bypassing protection.

Rate-limited responses use HTTP 429 and `Retry-After: 60`.

## Request-body size limit

`RequestBodySizeLimitFilter` applies to `/api/` POST, PUT and PATCH methods. Maximum body size is 1 MiB. It checks declared content length and reads at most `1 MiB + 1 byte`, covering chunked/incorrect `Content-Length` cases. Oversized input returns HTTP 413 with code `PAYLOAD_TOO_LARGE`.

## CSRF/session behavior

The normal bearer API is stateless and does not authenticate with browser cookies/sessions. Admin is also configured stateless. Review CSRF behavior carefully if authentication transport ever changes to cookies.

## Security change checklist

1. Decide which chain owns the endpoint.
2. Add explicit public exceptions only when required.
3. Confirm role rules for admin mutations.
4. Update CORS headers/origins only for real clients.
5. Add a rate-limit bucket only when route characteristics justify it.
6. Keep provider/webhook secrets out of logs and errors.
7. Run integration tests for unauthenticated, unauthorized and authorized cases.
