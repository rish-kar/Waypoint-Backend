# PostgreSQL and Flyway

## Source of truth

PostgreSQL is the durable system of record. JPA maps application entities, while Flyway owns schema creation/evolution. Hibernate is configured with `ddl-auto=validate`, so application startup validates mappings but does not silently mutate production schema.

`open-in-view=false` keeps persistence access inside explicit service/repository boundaries rather than allowing lazy database reads during response rendering.

## Major persisted domains

The current schema supports at least:

- users/provider identity;
- plan catalogue;
- subscriptions and provider external IDs/status/timestamps;
- Premium Special grants;
- webhook event processing records;
- admin audit events;
- billing checkout sessions/intents/coordination;
- revoked JWT token IDs and expiry;
- administrator accounts/roles/TOTP-related state.

## Flyway policy

Migrations live under `src/main/resources/db/migration/` and are append-only once released. Never edit an applied production migration to change history; add a new version.

The current evolution includes migrations through V15. Important later versions include:

- V3 — special premium grants;
- V4 — admin audit events;
- V5 — webhook attempt tracking;
- V6 — subscription trial-end data;
- V7 — synchronized plan catalogue/prices;
- V8 — provider event time;
- V9 — billing checkout sessions;
- V10 — revoked JWT tokens;
- V11 — webhook payload redaction;
- V12 — admin accounts;
- V13 — hot-query indexes;
- V14 — distributed-rate-limit related evolution;
- V15 — checkout intents.

Earlier V1/V2 establish the initial user/plan/subscription/webhook foundation.

## Migration startup behavior

Flyway is enabled with `clean-disabled=true` and `validate-on-migrate=true`. Production should fail rather than automatically drop/rebuild a mismatched schema.

## Connection pool

Hikari settings are environment configurable:

```text
DATABASE_POOL_MAX_SIZE
DATABASE_POOL_MIN_IDLE
DATABASE_CONNECTION_TIMEOUT_MS
DATABASE_VALIDATION_TIMEOUT_MS
```

Production defaults use a larger pool than shared configuration. Size pools against the actual database connection budget across all application instances.

## PostgreSQL integration tests

The Maven `postgres-it` profile runs `*PostgresIT.java` through Failsafe/Testcontainers. Use it for migration/query behavior that H2 compatibility mode cannot guarantee:

```bash
mvn -Ppostgres-it verify
```

CI runs this separately, so every migration must work against a real PostgreSQL container.

## Schema change procedure

1. Add a new Flyway version; never renumber existing released files.
2. Update JPA entity/repository code.
3. Add/adjust service tests.
4. Run `mvn clean verify`.
5. Run `mvn -Ppostgres-it verify`.
6. Test upgrade from a representative previous schema/data set for destructive or large migrations.
7. Document backfill, locking or rollout implications.
