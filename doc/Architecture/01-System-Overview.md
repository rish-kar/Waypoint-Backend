# System Overview

## Purpose

Waypoint-Backend provides server-owned identity, subscription and entitlement state for the Waypoint product. It validates Google identity, issues Waypoint JWTs, creates Lemon Squeezy checkouts, consumes signed billing webhooks, reconciles provider subscription state, resolves feature access and exposes a controlled administrative API.

## Runtime topology

```text
Browser / future Waypoint API client
        |
        | HTTPS JSON
        v
Spring Security + request filters
        |
        v
REST controllers
        |
        v
Application services
  |          |          |
  |          |          +---- Google / Lemon Squeezy via WebClient
  |          |
  |          +--------------- Redis distributed state
  |
  +-------------------------- JPA repositories
                                |
                                v
                           PostgreSQL
```

Lemon Squeezy and Google are external trust boundaries. Provider responses are validated and normalized before becoming local state.

## Technology stack

`pom.xml` targets Java 21 and Spring Boot 4.1.0. Major dependencies include Spring Web, WebFlux for outbound HTTP, Spring Security, Spring Data JPA, Spring Data Redis, Bean Validation, Actuator, Flyway and PostgreSQL. Tests add H2, Spring test/security support and Testcontainers PostgreSQL.

## Modular monolith organization

Package root: `com.waypoint.backend`.

Major responsibilities are separated into packages for controllers, services, repositories, models/entities, configuration, security, web/error infrastructure and external clients. They deploy as one Spring Boot process and one database schema, which keeps transactional business workflows local while preserving code-level module boundaries.

## Durable data and distributed state

PostgreSQL persists authoritative business records. Redis is used when `security.distributed-state-enabled=true`, which is the production default, for distributed rate-limit counters and fast JWT revocation state. JWT revocation records are also persisted in PostgreSQL; Redis lookup failure falls back to the database.

The development/default profile disables distributed security state so Redis-backed rate limits do not block local work, though Compose still provides Redis for parity.

## External systems

### Google

The backend accepts a Google access token at the auth endpoint, verifies it against configured Google endpoints/client audience, obtains verified identity data and upserts the local user. It never trusts an email string supplied independently by the frontend.

### Lemon Squeezy

The backend creates hosted checkouts using configured store/variant IDs. Provider subscription lifecycle events arrive at the webhook endpoint and are verified with HMAC-SHA256. Scheduled reconciliation provides a second path to converge local subscription state with the provider.

## Operational surfaces

Only Actuator health endpoints are exposed. Request logging uses request IDs and structured key-value output. CI validates the Postman collection, application tests, real PostgreSQL migrations and dependency changes.

## Key design decisions

1. Local database state is the entitlement read path; user requests do not depend on a live Lemon Squeezy lookup.
2. Authentication and admin authorization are stateless HTTP APIs.
3. Admin traffic has a separate security chain and production TOTP.
4. Provider webhooks are authenticated, idempotent and recoverable.
5. Schema changes are explicit Flyway migrations; Hibernate uses `ddl-auto=validate`.
6. Production distributed security state is shared through Redis instead of per-instance memory.
