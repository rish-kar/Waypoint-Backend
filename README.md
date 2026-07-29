# Waypoint-Backend

Waypoint-Backend is a Spring Boot modular monolith for monetising the Waypoint browser extension. It handles Google sign-in, Waypoint JWT issuance, Lemon Squeezy checkout creation, signed payment webhooks, subscription storage, and premium entitlement responses.

## Architecture

- Spring Boot 4.1, Java 21, Maven
- Spring Web, Spring Security, Spring Data JPA, Bean Validation and Actuator
- PostgreSQL with Flyway migrations
- HMAC-signed Waypoint JWTs
- WebClient for Google and Lemon Squeezy HTTP calls
- SLF4J with Logback structured key-value console output
- Package root: `com.waypoint.backend`

The database is the source of truth for entitlements. The entitlement endpoint never calls Lemon Squeezy.

## Configuration Profiles

Waypoint uses four configuration layers:

| File | Purpose |
| --- | --- |
| `application.yml` | Shared server, JPA, Flyway, health and logging configuration |
| `application-dev.yml` | Local development defaults; this is the default profile |
| `application-test.yml` | H2-backed isolated test configuration |
| `application-prod.yml` | Production configuration with no secret fallbacks |

Production startup fails when required values are missing, malformed or still contain obvious development placeholders. Production also requires HTTPS backend URLs and explicit HTTPS or `chrome-extension://` CORS origins.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose for local PostgreSQL
- A Google OAuth client ID for real authentication testing
- Lemon Squeezy test-mode credentials for real checkout and webhook testing

## Environment Variables

Copy `.env.example` to `.env` for a local template. Never commit `.env` or real secrets.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `test` or `prod`; defaults to `dev` outside the container |
| `PORT` | HTTP port; defaults to `8080` |
| `DATABASE_URL` | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `DATABASE_POOL_MAX_SIZE` | Hikari maximum pool size |
| `DATABASE_POOL_MIN_IDLE` | Hikari minimum idle connections |
| `DATABASE_CONNECTION_TIMEOUT_MS` | Hikari connection timeout |
| `DATABASE_VALIDATION_TIMEOUT_MS` | Hikari validation timeout |
| `JWT_SECRET` | HMAC secret; minimum 32 characters and 32 bytes |
| `JWT_EXPIRATION_SECONDS` | JWT validity; defaults to `86400` |
| `GOOGLE_CLIENT_ID` | Expected Google OAuth client ID |
| `GOOGLE_TOKEN_INFO_URL` | Google token validation endpoint |
| `GOOGLE_USER_INFO_URL` | Google profile endpoint |
| `LEMON_SQUEEZY_API_KEY` | Server-side Lemon Squeezy API key |
| `LEMON_SQUEEZY_STORE_ID` | Lemon Squeezy store ID |
| `LEMON_SQUEEZY_MONTHLY_VARIANT_ID` | Monthly Premium variant ID |
| `LEMON_SQUEEZY_ANNUAL_VARIANT_ID` | Annual Premium variant ID |
| `LEMON_SQUEEZY_WEBHOOK_SECRET` | Webhook HMAC secret |
| `LEMON_SQUEEZY_API_BASE_URL` | Lemon Squeezy API base URL |
| `CORS_ALLOWED_ORIGINS` | Comma-separated explicit frontend origins |
| `APP_BASE_URL` | Public backend base URL |
| `LOG_LEVEL_ROOT` | Root logging level |
| `LOG_LEVEL_WAYPOINT` | `com.waypoint.backend` logging level |

## Local Startup

Start PostgreSQL only:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

The development profile supplies safe local placeholders for Google and Lemon Squeezy, so the application can start before real provider credentials are added. Authentication, checkout and webhook calls still require valid provider configuration to work.

Start the complete local stack:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080` and PostgreSQL at `localhost:5432`.

PowerShell with real local provider values:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/waypoint"
$env:DATABASE_USERNAME="waypoint"
$env:DATABASE_PASSWORD="waypoint"
$env:JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:GOOGLE_CLIENT_ID="your-google-client-id"
$env:LEMON_SQUEEZY_API_KEY="your-lemon-squeezy-api-key"
$env:LEMON_SQUEEZY_STORE_ID="your-store-id"
$env:LEMON_SQUEEZY_MONTHLY_VARIANT_ID="your-monthly-variant-id"
$env:LEMON_SQUEEZY_ANNUAL_VARIANT_ID="your-annual-variant-id"
$env:LEMON_SQUEEZY_WEBHOOK_SECRET="your-webhook-secret"
$env:CORS_ALLOWED_ORIGINS="chrome-extension://your-extension-id,http://localhost:5173"
$env:APP_BASE_URL="http://localhost:8080"
mvn spring-boot:run
```

## Production Startup

The production image defaults to the `prod` profile. Supply every required secret and connection variable through the deployment platform:

```bash
docker run --rm -p 8080:8080 --env-file .env waypoint-backend
```

For production:

- `APP_BASE_URL` must be a valid HTTPS URL.
- `CORS_ALLOWED_ORIGINS` must contain only explicit HTTPS or Chrome-extension origins.
- Development placeholders are rejected.
- Flyway validates migrations and Hibernate validates the mapped schema.

## Health Checks

Actuator exposes only health endpoints:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Readiness includes database connectivity. Detailed health information is visible in development and hidden in production.

## Logging

Application code uses SLF4J. Console logs use one-line key-value fields including application name, logger, thread and request ID.

Every HTTP request:

- accepts a safe incoming `X-Request-ID` or creates a UUID;
- returns the ID through `X-Request-ID`;
- stores it in SLF4J MDC;
- logs method, path, response status and duration without logging query strings, bodies, tokens or secrets.

## Google OAuth Configuration

Create a Google OAuth client for the browser extension and set `GOOGLE_CLIENT_ID` to that client ID. The extension sends only the Google access token to `POST /api/v1/auth/google`; the backend validates the token server-side, verifies the expected audience, fetches the Google profile, requires a provider user ID and verified email, and never trusts an email supplied directly by the extension.

## Lemon Squeezy Test Mode

Create monthly and annual Premium variants and configure their IDs. Checkout custom data contains:

```json
{
  "waypoint_user_id": "internal-user-uuid",
  "waypoint_plan": "MONTHLY"
}
```

## Webhook Configuration

Configure subscription lifecycle events and `subscription_payment_refunded` at:

```text
POST https://your-backend.example/api/v1/webhooks/lemonsqueezy
```

The backend verifies `X-Signature` using HMAC-SHA256, stores a raw payload hash for idempotency and links subscriptions through `meta.custom_data.waypoint_user_id`. Do not subscribe to `order_refunded` until order-to-subscription mapping is implemented.

## API Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/google` | Public | Validate Google access token and issue a Waypoint JWT |
| `GET` | `/api/v1/me` | JWT | Return user profile and entitlement |
| `GET` | `/api/v1/entitlements` | JWT | Return current feature entitlement |
| `POST` | `/api/v1/billing/checkout` | JWT | Create Lemon Squeezy hosted checkout |
| `GET` | `/api/v1/billing/status` | JWT | Return stored subscription status |
| `POST` | `/api/v1/webhooks/lemonsqueezy` | Signed webhook | Process subscription and refund events |
| `GET` | `/actuator/health/**` | Public | Health, liveness and readiness checks |

## Subscription Rules

- `ACTIVE`: Premium
- `ON_TRIAL`: Premium
- `CANCELLED` with a future `ends_at`: Premium until `ends_at`
- `EXPIRED`: Free
- `REFUNDED`: Free
- No subscription: Free
- Unknown or malformed status: Free

Free users receive only `instant-tab-search`. Premium users receive all Waypoint features.

## Tests

```bash
mvn test
mvn verify
```

To explicitly load the isolated test profile:

```bash
mvn verify -Dspring.profiles.active=test
```

The main integration suite uses H2 in PostgreSQL compatibility mode and mocks Google and Lemon Squeezy clients. Testcontainers dependencies remain available for future PostgreSQL-backed integration tests.

## Docker Image

```bash
docker build -t waypoint-backend .
```

The runtime image:

- uses Java 21;
- runs as the non-root `waypoint` user;
- defaults to the production profile;
- expects runtime configuration through environment variables.

## Current Limitations

- No refresh tokens or JWT revocation.
- No rate limiting.
- No customer billing portal or admin dashboard.
- No background Lemon Squeezy reconciliation job.
- Webhook idempotency uses the raw body hash, so differently formatted equivalent payloads are treated as separate events.
