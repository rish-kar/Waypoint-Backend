# Test Strategy and Procedures

## Test layers

The backend uses unit/service tests, Spring application/security integration tests, H2-isolated database tests, real PostgreSQL/Testcontainers integration tests, provider-client mocks and generated Postman contract tooling.

## Standard verification

```bash
mvn clean verify
```

This is the baseline local/CI command. It compiles, runs the standard test suite and executes verification lifecycle checks.

## Real PostgreSQL verification

```bash
mvn -Ppostgres-it verify
```

The `postgres-it` Maven profile configures Failsafe to execute `**/*PostgresIT.java`. Testcontainers starts PostgreSQL so Flyway SQL, indexes/types/constraints and repository behavior are tested against the production database family rather than only H2 compatibility mode.

Docker must be available for this profile.

## What to test by responsibility

### Authentication/JWT

- valid/invalid Google token responses;
- audience mismatch/unverified identity;
- user upsert/login behavior;
- JWT signature/expiry/claims;
- revoked JWT rejection and database fallback.

### Security

- public vs authenticated paths;
- admin chain isolation from user JWTs;
- ADMIN vs SUPER_ADMIN authorization;
- production TOTP;
- CORS origin/header behavior;
- rate-limit responses;
- 1 MiB body limit including chunked input.

### Billing/webhooks

- plan-to-variant mapping;
- checkout idempotency/coordination;
- provider error mapping;
- valid/invalid webhook signature;
- event replay/idempotency;
- out-of-order provider event handling;
- retry/recovery state;
- reconciliation.

### Entitlements

Cover all subscription statuses, end dates, trial behavior and Premium Special precedence/fallback.

### Admin

Cover filters/paging, credential rejection, role restrictions, typed mutations and resulting audit rows.

### Persistence

Every new Flyway migration must pass the PostgreSQL profile. Add repository tests for new hot queries/constraints.

## CI parity

CI runs both `mvn -B clean verify` and `mvn -B -Ppostgres-it verify` in separate jobs. Do not consider a migration complete when only H2 tests pass.

## Manual pre-release smoke test

1. Start Postgres + Redis + backend with production-like non-secret test configuration.
2. Check liveness/readiness.
3. Authenticate a test Google account.
4. Exercise protected account/entitlement endpoint.
5. Create a Lemon Squeezy test checkout and deliver test webhooks.
6. Verify subscription/entitlement convergence.
7. Exercise admin read and one safe test mutation with TOTP-enabled configuration.
8. Confirm rate/body limits and CORS from intended origin.
9. Inspect logs for request IDs and absence of secrets/body dumps.
