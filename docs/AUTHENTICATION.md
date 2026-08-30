# Authentication

Waypoint uses backend-controlled Google OAuth for browser-extension sign-in. Provider credentials and provider-token handling stay on the backend. After Google authentication succeeds, Waypoint issues a short-lived HMAC-SHA256 access JWT plus a rotating opaque refresh token.

## Browser-extension Google flow

1. The extension opens `GET /api/v1/auth/google/start` with its `chromiumapp.org` return URL.
2. The backend creates a one-time OAuth state and PKCE verifier, then redirects the browser to Google.
3. Google redirects to `GET /api/v1/auth/google/callback` on the Waypoint backend.
4. The backend exchanges the authorization code using `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
5. The backend validates the Google profile, creates or updates the Waypoint user, and synchronizes the user's plan.
6. Waypoint creates an access JWT and an opaque refresh session.
7. The callback redirects to the validated extension return URL with the Waypoint access and refresh credentials in the URL fragment.
8. The extension stores the access token in Chrome session storage and the refresh token in Waypoint local storage.
9. Protected API calls use the access JWT. When it is near expiry or receives a `401`, the extension rotates the refresh token and retries once automatically.

The extension does not contain Google OAuth client credentials, does not call Google token endpoints, and does not receive or persist Google provider tokens.

The direct `POST /api/v1/auth/google` endpoint remains available for backend/API testing with a real Google access token whose audience matches `GOOGLE_CLIENT_ID`.

## Login endpoints

Start the browser OAuth flow:

```http
GET /api/v1/auth/google/start?returnUrl=https://<extension-id>.chromiumapp.org/google
```

Configured Google callback:

```http
GET /api/v1/auth/google/callback
```

Direct API login:

```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "accessToken": "google-oauth-access-token"
}
```

A successful Waypoint login/session response contains both token types:

```json
{
  "accessToken": "waypoint-jwt",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "refreshToken": "opaque-one-time-refresh-token",
  "refreshExpiresIn": 2592000,
  "user": {},
  "entitlement": {}
}
```

## Refresh sessions

Rotate a Waypoint session without requiring a valid access JWT:

```http
POST /api/v1/auth/session/refresh
Content-Type: application/json

{
  "refreshToken": "opaque-one-time-refresh-token"
}
```

A successful refresh returns a new access JWT and a new refresh token. The previous refresh token is immediately revoked and cannot be replayed.

Refresh-session properties:

- refresh tokens are generated from cryptographically secure random bytes;
- only the SHA-256 hash of a refresh token is stored in PostgreSQL;
- refresh rotation is serialized with a pessimistic database lock;
- expired and replayed refresh tokens return `401 UNAUTHORIZED`;
- logout revokes all active refresh sessions for the Waypoint user;
- expired and old revoked refresh-session rows are cleaned periodically;
- the default refresh-session lifetime is 30 days.

A browser profile created before refresh-token support must sign in once after upgrading. New sessions then refresh transparently until the refresh-session lifetime expires or the session is revoked.

## Waypoint access JWT

Waypoint access tokens use `HS256` and contain:

- `iss`: `waypoint-backend`;
- `aud`: `waypoint-extension`;
- `sub`: internal Waypoint user UUID;
- `email`: verified normalized email;
- `jti`: unique token UUID;
- `iat`: issued-at time;
- `nbf`: not-before time;
- `exp`: expiration time.

The verifier validates token length, signature, algorithm, type, issuer, audience, subject, email, token ID and time claims. Invalid and expired tokens deliberately return the same client-facing authentication failure.

Protected request example:

```http
GET /api/v1/account
Authorization: Bearer waypoint-jwt
```

The Google start/callback routes and refresh route are public because they establish or renew authentication. Protected account, subscription, entitlement, billing and AI routes require a valid Waypoint access JWT.

## Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer waypoint-jwt
```

Logout revokes the presented access JWT and all active refresh sessions for the user. The extension also clears its local Waypoint authentication state.

## Configuration

Development defaults are in `application-dev.yml`. Local/backend-controlled Google OAuth requires:

```text
GOOGLE_CLIENT_ID=<google-web-application-client-id>
GOOGLE_CLIENT_SECRET=<google-web-application-client-secret>
APP_BASE_URL=http://localhost:8080
JWT_SECRET=<at-least-32-random-bytes>
JWT_EXPIRATION_SECONDS=86400
WAYPOINT_SESSION_REFRESH_TOKEN_TTL_SECONDS=2592000
WAYPOINT_SESSION_CLEANUP_MS=3600000
```

Google Cloud must authorize this exact local redirect URI:

```text
http://localhost:8080/api/v1/auth/google/callback
```

Never place `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`, Google provider tokens, Waypoint access tokens or raw Waypoint refresh tokens in source control or logs.

## Account phone update

Phone is optional Waypoint profile data. The current Google scope (`openid email profile`) does not supply a phone number, so the user enters it manually.

```http
PATCH /api/v1/account
Authorization: Bearer waypoint-jwt
Content-Type: application/json

{
  "phoneNumber": "+91 9916604905",
  "phoneCountryCode": "IN"
}
```

The response is the updated account. The phone number and ISO country code are persisted on the Waypoint user row.

## Automated tests

Run the complete suite:

```bash
mvn clean verify
```

Authentication coverage includes Google login/profile validation, protected endpoint enforcement, JWT validation/revocation, refresh-session rotation/replay rejection and the phone-update path after an access token expires.

`WaypointSessionIntegrationTests` specifically exercises:

```text
login → access JWT expires → refresh-token rotation → PATCH /api/v1/account
→ phone persisted in DB → old refresh token rejected → logout → refresh session revoked
```

## Postman

The importable backend collection remains:

```text
postman/Waypoint-Backend.postman_collection.json
postman/Waypoint-Local.postman_environment.json
```

The direct Google login requests are useful for provider-token API testing. The browser-extension OAuth callback and transparent token-retry behavior are covered by the application integration tests because those flows depend on Chrome's identity redirect transport.
