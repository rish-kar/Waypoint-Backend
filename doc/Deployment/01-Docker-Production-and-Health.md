# Docker, Production and Health

## Local Compose topology

`docker-compose.yml` defines:

```text
postgres: postgres:16
redis:    redis:7-alpine
backend:  local Dockerfile build
```

PostgreSQL has a persistent named volume and health check. Local Redis disables RDB/AOF persistence because its local coordination keys are ephemeral; durable revocation/business state remains in PostgreSQL. Backend waits for healthy Postgres and Redis.

A `POSTGRES_PASSWORD` value is required by Compose.

## Docker image

Current multi-stage Dockerfile:

- build: `maven:3.9.15-eclipse-temurin-26`;
- Maven compiles/tests the project with Java target level 21;
- runtime: `eclipse-temurin:25-jre-alpine`;
- creates non-root `waypoint` user/group;
- runs with `-XX:MaxRAMPercentage=75.0`;
- exposes port 8080.

If build/runtime JDK images are changed, verify the Java 21 bytecode target and rerun the full test suite.

## Production prerequisites

Provide externally managed:

- PostgreSQL with backups and restricted network access;
- Redis with restricted network access and appropriate TLS/auth;
- all JWT/admin/provider secrets;
- HTTPS ingress/reverse proxy/load balancer;
- explicit CORS origins;
- production `APP_BASE_URL`.

Set `SPRING_PROFILES_ACTIVE=prod`.

## Health probes

Actuator exposes only health endpoints. Liveness includes application liveness state; readiness includes readiness state and database health.

Use:

```text
/actuator/health/liveness
/actuator/health/readiness
```

for orchestration probes. Do not expose arbitrary Actuator endpoints without a security review.

## Graceful shutdown

Server shutdown is graceful and Spring lifecycle timeout is configured to 20 seconds. Deployment platforms should give the container at least that shutdown window before force kill so in-flight requests/transactions can complete.

## Database rollout

Flyway runs at application startup. For potentially long/locking migrations, assess zero-downtime compatibility before deploying multiple versions concurrently. Prefer expand/migrate/contract patterns for breaking schema changes.

## Production checklist

1. `mvn clean verify` and `mvn -Ppostgres-it verify` pass.
2. Docker image builds from a clean checkout.
3. No production secrets exist in image layers/source.
4. Production profile starts with intended secret/config injection.
5. HTTPS enforcement works behind forwarded headers.
6. CORS contains only intended HTTPS/extension origins.
7. Redis distributed state is enabled/reachable.
8. Readiness includes healthy DB.
9. Login, entitlement, checkout/webhook and admin/TOTP smoke tests pass.
10. Logs/metrics/alerts and database backups are configured before traffic cutover.
