# Environment, Profiles and Startup

## Configuration files

- `application.yml` — shared defaults, JPA/Flyway/health/logging and default `dev` profile.
- `application-dev.yml` — local-development overrides/placeholders.
- `application-test.yml` — isolated H2-backed tests.
- `application-prod.yml` — production external configuration and distributed state.

## Core environment variables

### Server/database/Redis

```text
SPRING_PROFILES_ACTIVE
PORT
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
DATABASE_POOL_MAX_SIZE
DATABASE_POOL_MIN_IDLE
DATABASE_CONNECTION_TIMEOUT_MS
DATABASE_VALIDATION_TIMEOUT_MS
REDIS_URL
```

### Admin/security

```text
ADMIN_ID
ADMIN_PASSWORD
ADMIN_TOTP_SECRET
ADMIN_TOTP_ENCRYPTION_KEY
JWT_SECRET
JWT_EXPIRATION_SECONDS
```

`security.distributed-state-enabled` is false in shared/default configuration and true in production. Treat changing it in production as a security architecture change.

### Google

```text
GOOGLE_CLIENT_ID
GOOGLE_TOKEN_INFO_URL
GOOGLE_USER_INFO_URL
```

### Lemon Squeezy

```text
LEMON_SQUEEZY_API_KEY
LEMON_SQUEEZY_STORE_ID
LEMON_SQUEEZY_MONTHLY_VARIANT_ID
LEMON_SQUEEZY_ANNUAL_VARIANT_ID
LEMON_SQUEEZY_WEBHOOK_SECRET
LEMON_SQUEEZY_API_BASE_URL
LEMON_SQUEEZY_RECONCILIATION_ENABLED
LEMON_SQUEEZY_RECONCILIATION_INITIAL_DELAY_MS
LEMON_SQUEEZY_RECONCILIATION_INTERVAL_MS
```

### HTTP/application/logging

```text
CORS_ALLOWED_ORIGINS
APP_BASE_URL
LOG_LEVEL_ROOT
LOG_LEVEL_WAYPOINT
```

## Production behavior

`application-prod.yml` requires database credentials, `REDIS_URL`, JWT secret, Google client ID, Lemon Squeezy credentials/variant IDs/webhook secret, CORS origins and app base URL. It enables distributed state and subscription reconciliation.

Spring Security requires HTTPS for both admin and normal API chains in production. `APP_BASE_URL` and public origins should use HTTPS; Chrome-extension origins are explicit exceptions for extension CORS where needed.

## Secrets

Never commit real secrets to YAML, Compose overrides, Postman files or shell scripts. Supply them from the deployment platform's secret manager/environment injection.

The Compose file contains development defaults for some non-production values, but `POSTGRES_PASSWORD` is explicitly required. Do not copy those defaults into production.

## Startup validation

Production should fail fast when required values are missing, malformed or still obvious development placeholders. Database startup also runs Flyway validation/migration and Hibernate mapping validation.

## Configuration change procedure

1. Add a typed/configured property with safe default only if a default is actually safe.
2. Add production validation for required secrets/URLs.
3. Update Compose only for local developer usability.
4. Add tests for invalid production configuration.
5. Update this document and deployment configuration together.
