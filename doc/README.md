# Waypoint Backend Engineering Documentation

This directory is the master implementation reference for **Waypoint-Backend**. The `Documentation` branch is rebuilt from the current `main` codebase so the documentation reflects the actual runtime rather than the older documentation branch snapshot.

## Documentation map

### Architecture
1. [System overview](Architecture/01-System-Overview.md)
2. [Request lifecycle and layers](Architecture/02-Request-Lifecycle-and-Layers.md)

### API
1. [HTTP API reference](API/01-HTTP-API-Reference.md)

### Security
1. [Google OAuth, Waypoint JWTs and revocation](Security/01-Google-OAuth-JWT-and-Revocation.md)
2. [Spring Security, CORS, rate limits and request limits](Security/02-Spring-Security-CORS-and-Rate-Limits.md)
3. [Admin authentication, TOTP and roles](Security/03-Admin-Authentication-TOTP-and-Roles.md)
4. [Webhook security, idempotency and recovery](Security/04-Webhook-Security-and-Recovery.md)

### Data
1. [PostgreSQL data model and Flyway migrations](Data/01-PostgreSQL-and-Flyway.md)
2. [Redis and distributed state](Data/02-Redis-and-Distributed-State.md)

### Billing and entitlements
1. [Lemon Squeezy checkout and subscription lifecycle](Billing/01-Lemon-Squeezy-Lifecycle.md)
2. [Plans, subscriptions and Premium Special](Entitlements/01-Plans-and-Entitlements.md)

### Administration
1. [Admin API, audit and operations](Admin/01-Admin-API-and-Audit.md)

### Configuration
1. [Environment, profiles and startup validation](Configuration/01-Environment-Profiles-and-Startup.md)

### Testing
1. [Test strategy and procedures](Testing/01-Test-Strategy-and-Procedures.md)
2. [Postman and provider integration testing](Testing/02-Postman-and-Provider-Testing.md)

### Deployment
1. [Docker, production and health](Deployment/01-Docker-Production-and-Health.md)

### Operations
1. [Logging, reconciliation, recovery and observability](Operations/01-Logging-Reconciliation-and-Recovery.md)

### Development
1. [Repository structure and change workflow](Development/01-Repository-Structure-and-Workflow.md)

### Troubleshooting
1. [Developer and production troubleshooting](Troubleshooting/01-Troubleshooting.md)

## Architectural source-of-truth rules

- PostgreSQL is the durable source of truth for users, plans, subscriptions, special grants, webhook records, admin audit/account state, checkout coordination and JWT revocation records.
- Redis is enabled as distributed production state for rate limiting and fast revocation/coordination paths; code falls back to PostgreSQL for JWT revocation lookup if Redis is unavailable.
- Effective entitlement resolution does not call Lemon Squeezy synchronously. Provider events/reconciliation update local state first, then entitlement APIs read local data.
- `/api/v1/admin/**` is separated from normal Waypoint bearer authentication by its own ordered Spring Security chain.
- Production enables distributed state, requires HTTPS, and requires external Redis configuration.
- Request bodies for API POST/PUT/PATCH endpoints are limited to 1 MiB.

## Frontend boundary

The current browser-extension repository does not contain `/api/v1/**` requests. These backend endpoints are ready server-side but are not documented as already connected to the frontend.

## Documentation maintenance rule

When an endpoint, entity, migration, security filter, provider workflow, environment variable or test gate changes, update the matching document in the same change. Avoid duplicate end-to-end documents that can drift; link responsibility documents through this master index.
