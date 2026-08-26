# Google OAuth, Waypoint JWTs and Revocation

## Trust model

The browser/provider token and the Waypoint API token are distinct credentials. Google proves provider identity; the backend then issues its own short-lived signed JWT for Waypoint APIs.

## Google login flow

```text
Client obtains Google access token
        |
POST /api/v1/auth/google
        |
Google token validation / profile lookup
        |
verify configured audience + provider subject + verified email
        |
resolve or create local user
        |
issue Waypoint JWT
```

The backend must derive trusted identity from Google's validated response. Do not accept a standalone client-supplied email as identity proof.

Configured values include `GOOGLE_CLIENT_ID`, `GOOGLE_TOKEN_INFO_URL` and `GOOGLE_USER_INFO_URL`.

## Waypoint JWT

JWTs are HMAC-signed using `JWT_SECRET`. Production secrets must be high-entropy and supplied externally. JWT expiration defaults to 86,400 seconds unless configured otherwise.

Protected requests are parsed by `JwtAuthenticationFilter`, which verifies signature/claims and establishes the authenticated user UUID used by controllers.

## Revocation

The current implementation **does support JWT revocation**. `JwtRevocationService` persists revoked token IDs in PostgreSQL (`RevokedJwtTokenEntity`) through `RevokedJwtTokenRepository`.

When distributed state is enabled, revocation is also cached in Redis under `waypoint:jwt:revoked:<tokenId>` with TTL equal to the token's remaining lifetime. Lookup behavior is:

1. check Redis in distributed mode;
2. if Redis lookup fails, fall back to PostgreSQL;
3. when distributed mode is disabled, check PostgreSQL directly.

A scheduled cleanup removes database revocation records after their JWT expiry (`jwt.revocation-cleanup-ms`, default one hour between cleanup runs).

This dual-store model prevents Redis loss from becoming the only record of a revoked token.

## Secret requirements

- Never commit `JWT_SECRET`.
- Production startup validation should reject missing/development placeholders.
- Rotate secrets as a controlled security event: changing the signing secret invalidates all tokens signed with the old key unless multi-key verification is deliberately added.

## Testing

JWT behavior has dedicated tests including `JwtRevocationIntegrationTests`. Security changes should cover valid token, expired token, malformed signature, revoked token, Redis-enabled/fallback behavior where applicable, and unauthenticated access to protected endpoints.
