# Redis and Distributed State

## Role

Redis is a production coordination/security store, not the primary business database. `application-prod.yml` requires `REDIS_URL` and sets `security.distributed-state-enabled=true`.

Development shared configuration defaults `security.distributed-state-enabled=false`, so local execution is not dependent on distributed rate state even though Docker Compose provides Redis.

## Rate limiting

`DistributedRateLimiter` uses a Lua script to atomically increment a per-minute key and set expiry on the first request. Prefix: `waypoint:rate:`. Production fails closed when Redis cannot execute the limiter.

Because rate keys are intentionally short-lived, Redis persistence is not required for that function. Compose therefore runs its local Redis with snapshot/AOF persistence disabled.

## JWT revocation cache

`JwtRevocationService` stores a Redis key `waypoint:jwt:revoked:<tokenId>` until token expiry when distributed state is enabled. The same revocation is also written to PostgreSQL. If a Redis lookup fails, revocation checking falls back to the database.

This is an important distinction from rate limiting: rate limiting intentionally fails closed on Redis failure, while revocation can safely fall back to durable state.

## Other distributed coordination

The schema/codebase includes checkout coordination and distributed-state evolution intended to prevent multi-instance races. Keep distributed locks/intent records bounded with TTLs and durable provider/business identifiers where recovery requires persistence.

## Production requirements

- Use a private Redis endpoint accessible only by application infrastructure.
- Require authentication/TLS according to the deployment platform.
- Monitor latency, connection exhaustion and command errors.
- Do not store raw provider tokens, webhook payloads or user content in rate/revocation keys.
- Capacity-plan based on request rate and active JWT volume, not database row count.

## Failure testing

Test Redis loss explicitly. Confirm:

- normal distributed rate-limited requests fail safely;
- revoked JWT lookup falls back to PostgreSQL;
- application health/alerts make Redis degradation visible at the platform layer;
- Redis recovery does not require clearing durable PostgreSQL state.
