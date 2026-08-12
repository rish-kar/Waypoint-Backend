# Authentication

Waypoint uses Google OAuth access tokens for initial sign-in and issues its own short-lived HMAC-SHA256 bearer token for subsequent API requests.

## Authentication flow

1. The browser extension obtains a Google OAuth access token.
2. The extension sends only that token to `POST /api/v1/auth/google`.
3. The backend calls Google's token-info and user-info endpoints.
4. The backend requires:
   - a non-empty Google subject;
   - a verified email address;
   - an audience equal to `GOOGLE_CLIENT_ID`;
   - a positive remaining token lifetime;
   - consistent subject and email values when both Google responses provide them.
5. The backend creates or updates the local user identified by the Google provider subject.
6. The backend returns a Waypoint bearer token, user profile and current entitlement.

The backend never accepts a client-supplied email address or Google user ID.

## Endpoint

```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "accessToken": "google-oauth-access-token"
}
```

Successful response:

```json
{
  "accessToken": "waypoint-jwt",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {},
  "entitlement": {}
}
```

Validation failures return `400 INVALID_REQUEST`. Invalid, expired or mismatched Google credentials return `401 UNAUTHORIZED` with the generic message `Google authentication failed`. A provider timeout or server failure returns `503 AUTH_PROVIDER_UNAVAILABLE`.

## Waypoint JWT

Waypoint tokens use `HS256` and contain:

- `iss`: `waypoint-backend`;
- `aud`: `waypoint-extension`;
- `sub`: internal Waypoint user UUID;
- `email`: verified normalized email;
- `jti`: unique token UUID;
- `iat`: issued-at time;
- `nbf`: not-before time;
- `exp`: expiration time.

The verifier checks the signature with constant-time comparison and validates token size, structure, algorithm, type, issuer, audience, subject, email, token ID and all time claims. Invalid and expired tokens deliberately return the same client-facing message.

Protected request example:

```http
GET /api/v1/account
Authorization: Bearer waypoint-jwt
```

Public endpoints are limited to Google login, Lemon Squeezy webhooks and Actuator health checks. All other routes require a valid Waypoint bearer token.

## Configuration

Development defaults are in `application-dev.yml`. Production must provide:

```text
GOOGLE_CLIENT_ID=<google-oauth-client-id>
GOOGLE_TOKEN_INFO_URL=https://oauth2.googleapis.com/tokeninfo
GOOGLE_USER_INFO_URL=https://www.googleapis.com/oauth2/v3/userinfo
JWT_SECRET=<at-least-32-random-bytes>
JWT_EXPIRATION_SECONDS=86400
```

Never place `JWT_SECRET` or Google access tokens in source control, request logs or error responses.

## Authentication logs

SLF4J authentication events include only operational fields:

- `authentication_succeeded`: provider and internal user ID;
- `authentication_rejected`: provider and sanitized reason code;
- `authentication_provider_unavailable`: provider;
- `bearer_token_accepted`: internal user ID, method and path at DEBUG level;
- `bearer_token_rejected`: sanitized reason, method and path.

Access tokens, JWTs and email addresses are not logged.

## Automated tests

Run the complete suite:

```bash
mvn clean verify
```

Authentication coverage includes:

- new and repeat Google login;
- invalid Google credentials;
- missing subject, unverified email, wrong audience and expired Google token;
- protected endpoint enforcement;
- malformed, tampered, future-issued, wrongly signed and expired Waypoint JWTs;
- missing JWT subject data.

## Postman

Authentication tests are part of the existing backend Postman collection. Import only:

```text
postman/Waypoint-Backend.postman_collection.json
postman/Waypoint-Local.postman_environment.json
```

Select `Waypoint Local` and run `01 - Authentication`. Set `googleAccessToken` before `Google Login`. The successful login automatically stores `jwt` and `userId`. The expired-token request generates and signs an already expired JWT using the `jwtSecret` value from the same environment.

When `JWT_SECRET` is overridden for the running backend, update `jwtSecret` in `Waypoint Local` to the same value.

## Current limitation

The current API does not issue refresh tokens and does not maintain a JWT revocation list. Users must authenticate with Google again after the Waypoint token expires.
