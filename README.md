# Waypoint-Backend

Waypoint-Backend is the server-side account, billing and entitlement service for Waypoint. It is implemented as a Spring Boot modular monolith with PostgreSQL as the durable business-data store and Redis as production distributed security/coordination state.

## Current stack

- Java source/target level: 21
- Spring Boot: 4.1.0
- Maven
- Spring Web + WebFlux `WebClient`
- Spring Security
- Spring Data JPA
- Spring Data Redis
- PostgreSQL + Flyway
- H2 for the default isolated test profile
- Testcontainers PostgreSQL integration tests
- Actuator health probes
- Lemon Squeezy billing integration
- Google OAuth token validation
- HMAC-signed Waypoint JWTs with persisted revocation
- Admin Basic authentication, roles and production TOTP
- Redis-backed production rate limits

## Documentation

The complete engineering reference is in [`doc/README.md`](doc/README.md). It covers architecture, API contracts, Google OAuth/JWTs, admin security/TOTP, rate limiting, webhook processing/recovery, database migrations, Redis, billing, entitlements, testing, Docker/production deployment, operations and troubleshooting.

## Local development

Prerequisites: Java 21+, Maven 3.9+, Docker/Compose for the container stack.

Start infrastructure and application using Compose:

```bash
export POSTGRES_PASSWORD=waypoint-local-password
docker compose up --build
```

Or run PostgreSQL/Redis separately and start Spring Boot with the required development environment values.

Default HTTP port: `8080`.

Health endpoints:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

## Tests

```bash
mvn clean verify
mvn -Ppostgres-it verify
```

The first command runs the standard unit/application integration suite. The `postgres-it` profile runs PostgreSQL/Testcontainers migration and database integration coverage.

## Core API groups

```text
/api/v1/auth/**             Google sign-in / JWT lifecycle
/api/v1/account             authenticated account view
/api/v1/billing/**          plans, checkout and billing state
/api/v1/subscriptions/**    current subscription
/api/v1/entitlements/**     effective feature access
/api/v1/webhooks/**         signed Lemon Squeezy events
/api/v1/admin/**            protected administrative operations
```

See [`doc/API/01-HTTP-API-Reference.md`](doc/API/01-HTTP-API-Reference.md) for the maintained endpoint list.

## Production security summary

The `prod` profile requires PostgreSQL, Redis, JWT, Google, Lemon Squeezy, CORS and public base URL configuration. It enables distributed security state. HTTPS is required by the Spring Security chains. Admin operations use a separate stateless Basic-auth chain; production additionally requires the admin TOTP filter. Request bodies for API POST/PUT/PATCH operations are capped at 1 MiB. Distributed rate limits use Redis and fail closed when the limiter cannot safely decide.

## Docker image

The current Dockerfile builds with `maven:3.9.15-eclipse-temurin-26`, compiles the project for Java 21, and runs the resulting jar on `eclipse-temurin:25-jre-alpine` as the non-root `waypoint` user.

## Frontend integration status

The backend API is implemented, but the current `rish-kar/Waypoint` frontend `main` branch does not yet call `/api/v1/**`. Treat frontend/backend wiring as an integration boundary until the extension contains a tested API client.
