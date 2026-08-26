# Developer and Production Troubleshooting

## Application does not start in production

Check `SPRING_PROFILES_ACTIVE=prod` and required DB/Redis/JWT/Google/Lemon/CORS/app/admin secrets. Production intentionally has no safe fallback for required external configuration. Look for startup validation, Flyway or Hibernate schema-validation failures before changing code.

## Database connection/readiness fails

Verify `DATABASE_URL`, username/password, network/TLS policy and Hikari limits. `GET /actuator/health/readiness` includes DB health. Check that total pool sizes across replicas fit the database connection budget.

## Flyway migration fails

Do not edit an already-applied migration. Reproduce with a copy/test database and run `mvn -Ppostgres-it verify`. Add a correcting forward migration if released history is wrong.

## Every production request gets 429

Distributed rate limiting fails closed when Redis operations fail. Check `REDIS_URL`, Redis availability/auth/TLS and application logs. Do not disable distributed state as a production workaround without an explicit security decision.

## Revoked JWT appears accepted/rejected unexpectedly

Check token ID/expiry and `revoked_jwt_tokens` durable state. In distributed mode Redis is the fast path, but lookup exceptions fall back to PostgreSQL. Redis keys expire at token expiry; database cleanup removes expired rows later.

## Google login reports audience/identity error

Verify the access token was issued for the configured `GOOGLE_CLIENT_ID`, not another OAuth client. Confirm provider user ID and verified email are present. Do not work around audience validation by accepting a client-provided email.

## Checkout succeeds but user remains Free

Trace the provider lifecycle: checkout custom data/user mapping -> signed webhook receipt -> webhook processing status -> subscription row -> entitlement resolution. Check provider event time and reconciliation. A browser redirect alone does not activate Premium.

## Webhook signature fails

Use the exact raw bytes received when calculating HMAC. Confirm `LEMON_SQUEEZY_WEBHOOK_SECRET` matches the provider endpoint. JSON whitespace/reformatting changes the signature input.

## Duplicate/out-of-order webhook behavior

Inspect admin webhook event records, external object ID, provider event timestamp, processing status and attempts. Replays should be idempotent; older provider state should not overwrite newer local state.

## Admin returns 401/403

401: validate Basic credentials and, in production, TOTP requirement. 403: check account role; mutations and admin-account operations require `SUPER_ADMIN`. A normal Waypoint JWT cannot authenticate admin endpoints.

## Request returns 413

API POST/PUT/PATCH bodies are capped at 1 MiB. Reduce payload size; do not raise the limit casually for webhook/admin endpoints because it is part of the abuse-control boundary.

## CI says Postman collection changed

Run:

```bash
python scripts/sync_postman_collection.py
git diff -- postman/Waypoint-Backend.postman_collection.json
```

Commit the correctly regenerated collection together with endpoint changes.

## H2 passes but PostgreSQL CI fails

Run `mvn -Ppostgres-it verify` with Docker available. PostgreSQL-specific SQL/types/index/constraint behavior is authoritative for production migrations.

## Frontend cannot call API

The current Waypoint frontend `main` has no backend API client. If testing a new integration branch, verify CORS includes the exact `chrome-extension://<extension-id>` origin, API base URL uses HTTPS in production, and token handling follows the integration design rather than assuming current frontend support.
