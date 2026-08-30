# Authentication

Waypoint supports backend-controlled Google OAuth and Microsoft OAuth. Provider credentials and provider-token handling stay on the backend. Successful authentication issues a short-lived Waypoint bearer JWT plus a rotating opaque Waypoint refresh token.

## Waypoint session model

A successful Google login or Microsoft session exchange returns:

```json
{
  "accessToken": "waypoint-jwt",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "opaque-waypoint-refresh-token",
  "refreshExpiresIn": 2592000,
  "user": {},
  "entitlement": {}
}
```

The access token is short-lived. The refresh token is opaque, stored only as a SHA-256 hash on the backend, rotated on every refresh and rejected after use, expiration or logout.

```http
GET /api/v1/account
Authorization: Bearer waypoint-jwt
```

```http
POST /api/v1/auth/session/refresh
Content-Type: application/json

{
  "refreshToken": "opaque-waypoint-refresh-token"
}
```

`GET /api/v1/auth/session` returns the authenticated session when a valid bearer token is present or a signed-out response when it is absent.

## Browser-extension Google flow

Google uses a backend-owned Web Application OAuth Authorization Code flow with PKCE.

1. The extension opens `GET /api/v1/auth/google/start` with its `chromiumapp.org` return URL using Chrome's web-auth transport.
2. The backend creates one-time state and a PKCE verifier and redirects to Google.
3. Google redirects to `GET /api/v1/auth/google/callback` on the Waypoint backend.
4. The backend exchanges the authorization code using `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
5. The backend validates the Google profile and creates or updates the Waypoint user.
6. Waypoint issues its own access and refresh credentials.
7. The callback returns only Waypoint credentials to the validated extension return URL; Google provider tokens are never exposed to the extension.

The extension does not contain Google client credentials and does not call Google token endpoints.

Start the browser flow:

```http
GET /api/v1/auth/google/start?returnUrl=https://<extension-id>.chromiumapp.org/google
```

Configured callback:

```text
http://localhost:8080/api/v1/auth/google/callback
```

The direct provider-token endpoint remains available for API testing:

```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "accessToken": "google-oauth-access-token"
}
```

The backend never trusts a client-supplied email address or Google user ID.

## Microsoft sign-in

Microsoft uses a backend-owned OAuth Authorization Code flow with PKCE.

### Start authentication

```http
POST /api/v1/auth/microsoft/start
Content-Type: application/json

{
  "redirectUri": "https://<extension-id>.chromiumapp.org/microsoft"
}
```

The `redirectUri` must exactly match one entry in `MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS`. The response contains an authorization URL, diagnostic transaction ID and expiration time. Waypoint stores only hashed state and keeps the PKCE verifier protected server-side.

### Microsoft callback

Microsoft redirects to:

```text
GET /api/v1/auth/microsoft/callback
```

The backend validates state, exchanges the code, reads the Microsoft Graph profile and stores the Microsoft refresh credential encrypted with AES-256-GCM. Microsoft provider tokens are never returned to the extension.

The backend returns a short-lived, one-time exchange code to the extension. Exchange it for a normal Waypoint session:

```http
POST /api/v1/auth/session/exchange
Content-Type: application/json

{
  "exchangeCode": "short-lived-one-time-code"
}
```

## Explicit Microsoft account linking

Waypoint does not automatically merge Google and Microsoft accounts by matching email addresses.

An authenticated Waypoint user can explicitly link Microsoft:

```http
POST /api/v1/auth/microsoft/link/start
Authorization: Bearer waypoint-jwt
Content-Type: application/json

{
  "redirectUri": "https://<extension-id>.chromiumapp.org/microsoft"
}
```

If that Microsoft identity is already linked to another Waypoint user, linking is rejected. For Google-primary accounts, the canonical Google email/display name remain unchanged after Microsoft is linked.

## Refresh sessions

Refresh tokens are generated from cryptographically secure random bytes. Only SHA-256 hashes are stored in PostgreSQL. Rotation is serialized with a pessimistic database lock. Expired or replayed refresh tokens return `401 UNAUTHORIZED`. Logout revokes all active refresh sessions for the user, and old rows are cleaned periodically.

A browser profile created before refresh-token support must sign in once after upgrading. New sessions then refresh transparently until the refresh lifetime expires or the session is revoked.

## Logout and Microsoft disconnect

Logout:

```http
POST /api/v1/auth/logout
Authorization: Bearer waypoint-jwt
```

Logout revokes the current JWT and all Waypoint refresh sessions. It does not delete an existing Microsoft link.

Explicit Microsoft disconnect:

```http
DELETE /api/v1/auth/microsoft
Authorization: Bearer waypoint-jwt
```

This removes the Microsoft credential/link while leaving the current Waypoint session valid.

## Account phone update

Phone is optional Waypoint profile data. The Google scope (`openid email profile`) does not provide a phone number, so users enter it manually.

```http
PATCH /api/v1/account
Authorization: Bearer waypoint-jwt
Content-Type: application/json

{
  "phoneNumber": "+91 9916604905",
  "phoneCountryCode": "IN"
}
```

The number and ISO country code are persisted on the Waypoint user and are also exposed through the admin user response.

## Local configuration

Google:

```text
GOOGLE_CLIENT_ID=<google-web-application-client-id>
GOOGLE_CLIENT_SECRET=<google-web-application-client-secret>
APP_BASE_URL=http://localhost:8080
```

Google Cloud must authorize:

```text
http://localhost:8080/api/v1/auth/google/callback
```

Microsoft:

```text
MICROSOFT_CLIENT_ID=<Azure app client ID>
MICROSOFT_CLIENT_SECRET=<Azure app client secret>
MICROSOFT_TENANT=common
MICROSOFT_CALLBACK_URL=http://localhost:8080/api/v1/auth/microsoft/callback
MICROSOFT_TOKEN_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS=https://<your-extension-id>.chromiumapp.org/microsoft
```

Azure must authorize:

```text
http://localhost:8080/api/v1/auth/microsoft/callback
```

Shared session configuration:

```text
JWT_SECRET=<at-least-32-random-bytes>
JWT_EXPIRATION_SECONDS=900
WAYPOINT_SESSION_REFRESH_TOKEN_TTL_SECONDS=2592000
WAYPOINT_SESSION_CLEANUP_MS=3600000
```

Never put provider secrets, provider tokens, Waypoint JWTs or raw Waypoint refresh tokens in source control or logs.

## Security properties

The OAuth/session design includes PKCE (`S256`), cryptographically random state, one-time state/exchange/refresh consumption, exact extension redirect validation, short-lived access JWTs, rotating Waypoint refresh sessions, sanitized provider failures, explicit cross-provider linking and no provider-token exposure to the extension. Microsoft refresh credentials are encrypted at rest.

## Automated tests

Run:

```bash
mvn clean verify
```

Coverage includes Google and Microsoft authentication, PKCE/scopes, invalid/expired/replayed state, session refresh rotation/replay rejection, persistent Microsoft links across logout, explicit disconnect/linking, provider credential rotation/failure cleanup, phone persistence and phone update after access-token expiry.

`WaypointSessionIntegrationTests` exercises:

```text
login → access JWT expires → refresh-token rotation → PATCH /api/v1/account
→ phone persisted in DB → old refresh token rejected → logout → refresh session revoked
```
