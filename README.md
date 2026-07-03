# Waypoint-Backend

Waypoint-Backend is a small Spring Boot modular monolith for monetising the Waypoint browser extension. It handles Google sign-in, Waypoint JWT issuance, Lemon Squeezy checkout creation, signed payment webhooks, subscription storage, and premium entitlement responses.

## Architecture

- Spring Boot 4.1, Java 21, Maven
- Spring Web, Spring Security, Spring Data JPA, Bean Validation, Actuator
- PostgreSQL with Flyway migrations
- HMAC-signed Waypoint JWTs
- WebClient for Google and Lemon Squeezy HTTP calls
- Package root: `com.waypoint.backend`

The database is the source of truth for entitlements. The entitlement endpoint never calls Lemon Squeezy.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose for local PostgreSQL
- A Google OAuth client ID
- Lemon Squeezy test-mode store, API key, variants, and webhook secret

## Environment Variables

Required outside tests:

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | JDBC URL, for example `jdbc:postgresql://localhost:5432/waypoint` |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | HMAC secret, at least 32 bytes |
| `JWT_EXPIRATION_SECONDS` | Defaults to `86400` |
| `GOOGLE_CLIENT_ID` | Expected Google OAuth client ID |
| `LEMON_SQUEEZY_API_KEY` | Server-side Lemon Squeezy API key |
| `LEMON_SQUEEZY_STORE_ID` | Lemon Squeezy store ID |
| `LEMON_SQUEEZY_MONTHLY_VARIANT_ID` | Monthly Premium variant ID |
| `LEMON_SQUEEZY_ANNUAL_VARIANT_ID` | Annual Premium variant ID |
| `LEMON_SQUEEZY_WEBHOOK_SECRET` | Webhook HMAC secret |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins, such as `chrome-extension://<id>,http://localhost:5173` |
| `APP_BASE_URL` | Public backend base URL |

Do not commit real secrets.

## Local PostgreSQL

```bash
docker compose up -d postgres
```

The compose file starts PostgreSQL on `localhost:5432` with database/user/password `waypoint`.

## Application Startup

PowerShell example:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/waypoint"
$env:DATABASE_USERNAME="waypoint"
$env:DATABASE_PASSWORD="waypoint"
$env:JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:JWT_EXPIRATION_SECONDS="86400"
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

## Google OAuth Configuration

Create a Google OAuth client for the browser extension and set `GOOGLE_CLIENT_ID` to that client ID. The extension sends only the Google access token to `POST /api/v1/auth/google`; the backend validates the token server-side, fetches the Google profile, requires a provider user ID and verified email, and never trusts an email supplied directly by the extension.

## Lemon Squeezy Test Mode

Create two test-mode Premium variants in Lemon Squeezy: monthly and annual. Put their IDs in `LEMON_SQUEEZY_MONTHLY_VARIANT_ID` and `LEMON_SQUEEZY_ANNUAL_VARIANT_ID`. The backend pre-fills the user's email and passes custom checkout data:

```json
{
  "waypoint_user_id": "internal-user-uuid",
  "waypoint_plan": "MONTHLY"
}
```

## Webhook Configuration

Configure Lemon Squeezy to send subscription and refund events to:

```text
POST https://your-backend.example/api/v1/webhooks/lemonsqueezy
```

Set the webhook signing secret as `LEMON_SQUEEZY_WEBHOOK_SECRET`. The backend verifies `X-Signature` with HMAC-SHA256, stores the raw payload hash for idempotency, and links subscriptions by `meta.custom_data.waypoint_user_id`.

## Example Curl Requests

Google login:

```bash
curl -X POST http://localhost:8080/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"accessToken":"google-oauth-access-token"}'
```

Current user:

```bash
curl http://localhost:8080/api/v1/me \
  -H "Authorization: Bearer WAYPOINT_JWT"
```

Entitlement:

```bash
curl http://localhost:8080/api/v1/entitlements \
  -H "Authorization: Bearer WAYPOINT_JWT"
```

Create checkout:

```bash
curl -X POST http://localhost:8080/api/v1/billing/checkout \
  -H "Authorization: Bearer WAYPOINT_JWT" \
  -H "Content-Type: application/json" \
  -d '{"plan":"MONTHLY"}'
```

Billing status:

```bash
curl http://localhost:8080/api/v1/billing/status \
  -H "Authorization: Bearer WAYPOINT_JWT"
```

## API Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/google` | Public | Validate Google access token, create/login user, return Waypoint JWT |
| `GET` | `/api/v1/me` | JWT | Return user profile and entitlement |
| `GET` | `/api/v1/entitlements` | JWT | Return current feature entitlement |
| `POST` | `/api/v1/billing/checkout` | JWT | Create Lemon Squeezy hosted checkout |
| `GET` | `/api/v1/billing/status` | JWT | Return stored subscription status |
| `POST` | `/api/v1/webhooks/lemonsqueezy` | Signed webhook | Process Lemon Squeezy subscription/refund events |
| `GET` | `/actuator/health` | Public | Health check |

## Subscription-State Rules

- `ACTIVE`: Premium
- `ON_TRIAL`: Premium
- `CANCELLED` with `ends_at` in the future: Premium until `ends_at`
- `EXPIRED`: Free
- `REFUNDED`: Free
- No subscription: Free
- Unknown or malformed status: Free by default

Free users receive only `instant-tab-search`. Premium users receive all Waypoint features.

## Tests

```bash
mvn test
mvn verify
```

The tests use H2 in PostgreSQL compatibility mode and mock external Google and Lemon Squeezy calls. Testcontainers dependencies are included for future PostgreSQL-backed integration expansion, but the MVP test suite avoids requiring Docker to be running.

## Docker

Build the production image:

```bash
docker build -t waypoint-backend .
```

Run with local compose PostgreSQL:

```bash
docker compose up -d postgres
docker run --rm -p 8080:8080 --env-file .env waypoint-backend
```

## MVP Security Limitations

- No refresh tokens; users must re-authenticate after the 24-hour JWT expires.
- No rate-limiting infrastructure.
- No customer billing portal or admin dashboard.
- No background reconciliation job with Lemon Squeezy.
- Webhook idempotency is based on the raw body hash, so semantically duplicate payloads with different formatting are treated as separate events.
- JWT revocation is not implemented; rotate `JWT_SECRET` in an emergency.
