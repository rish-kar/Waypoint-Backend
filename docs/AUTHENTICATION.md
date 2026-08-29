# Authentication

Waypoint supports Google and Microsoft sign-in and issues its own short-lived bearer token plus a rotating Waypoint refresh token for authenticated API access.

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

The access token is a short-lived Waypoint JWT. The refresh token is opaque, stored only as a SHA-256 hash on the backend, rotated on every refresh and rejected after use, expiration or logout.

Protected request example:

```http
GET /api/v1/account
Authorization: Bearer waypoint-jwt
```

Refresh an expired/expiring Waypoint session without an access token:

```http
POST /api/v1/auth/session/refresh
Content-Type: application/json

{
  "refreshToken": "opaque-waypoint-refresh-token"
}
```

`GET /api/v1/auth/session` is public and returns either the authenticated user/session when a valid bearer token is present or a signed-out response when it is absent.

## Google sign-in

1. The browser extension obtains a Google OAuth access token.
2. The extension sends only that token to `POST /api/v1/auth/google`.
3. The backend validates the Google token and profile using Google endpoints.
4. The backend creates or updates the user identified by the Google provider subject.
5. The backend returns a Waypoint access token, rotating refresh token, user profile and entitlement.

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

### 1. Start authentication

```http
POST /api/v1/auth/microsoft/start
Content-Type: application/json

{
  "redirectUri": "https://<extension-id>.chromiumapp.org/microsoft"
}
```

The `redirectUri` must exactly match one entry in `MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS`.

The response contains:

- `authorizationUrl` — open this in the browser/Chrome auth flow;
- `transactionId` — diagnostic identifier only;
- `expiresIn` — lifetime of the pending OAuth transaction.

Waypoint generates a random OAuth state and PKCE verifier, stores only the state hash, encrypts the verifier at rest and makes the state single-use.

### 2. Microsoft callback

Microsoft redirects to the backend callback configured by `MICROSOFT_CALLBACK_URL`:

```text
GET /api/v1/auth/microsoft/callback
```

The backend validates the state, exchanges the authorization code with Microsoft, requests the Microsoft Graph profile and stores the Microsoft refresh credential encrypted with AES-256-GCM.

The backend then redirects to the original extension redirect URI with either:

```text
?waypoint_auth=success&exchange_code=<short-lived-one-time-code>
```

or a sanitized error such as:

```text
?waypoint_auth=error&error=authentication_failed
```

Microsoft provider tokens are never returned to the extension.

### 3. Exchange for a Waypoint session

```http
POST /api/v1/auth/session/exchange
Content-Type: application/json

{
  "exchangeCode": "short-lived-one-time-code"
}
```

The exchange code is stored only as a hash, expires quickly and is single-use. A successful exchange returns the normal Waypoint access/refresh session response.

## Explicit Microsoft account linking

Waypoint does not automatically merge Google and Microsoft accounts by matching email addresses.

An already-authenticated Waypoint user can explicitly link Microsoft:

```http
POST /api/v1/auth/microsoft/link/start
Authorization: Bearer waypoint-jwt
Content-Type: application/json

{
  "redirectUri": "https://<extension-id>.chromiumapp.org/microsoft"
}
```

The authenticated Waypoint user ID is bound to the OAuth transaction. If that Microsoft identity is already linked to another Waypoint user, linking is rejected.

For Google-primary accounts, the canonical Google email/display name remain unchanged after Microsoft is linked.

## Logout vs Microsoft disconnect

Logout and provider unlinking are intentionally separate operations.

Logout:

```http
POST /api/v1/auth/logout
Authorization: Bearer waypoint-jwt
```

Logout revokes the current JWT and all Waypoint refresh sessions for the user. It **does not delete the Microsoft account link**, so an explicitly linked Microsoft identity remains associated with the same Waypoint account after signing out.

Explicit Microsoft disconnect:

```http
DELETE /api/v1/auth/microsoft
Authorization: Bearer waypoint-jwt
```

This removes the stored Microsoft provider credential/link while leaving the current Waypoint session valid.

## Microsoft credential refresh

The backend stores only the Microsoft refresh credential required for future Microsoft API access. It is encrypted at rest.

When refreshed:

- the backend sends the stored provider refresh token to Microsoft;
- a rotated Microsoft refresh token replaces the previous encrypted value;
- if Microsoft rejects the credential, the stored credential is removed and re-authentication/re-linking is required.

## Local end-to-end Microsoft setup

Production secret-management details can be added later. For a real local Microsoft E2E test, configure these values in your local environment:

```text
MICROSOFT_CLIENT_ID=<Azure app client ID>
MICROSOFT_CLIENT_SECRET=<Azure app client secret>
MICROSOFT_TENANT=common
MICROSOFT_CALLBACK_URL=http://localhost:8080/api/v1/auth/microsoft/callback
MICROSOFT_TOKEN_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS=https://<your-extension-id>.chromiumapp.org/microsoft
```

For the Azure/Microsoft app registration used locally, add this Web redirect URI exactly:

```text
http://localhost:8080/api/v1/auth/microsoft/callback
```

Then:

1. Start PostgreSQL and Redis and run the backend with the `dev` profile.
2. Import `postman/Waypoint-Backend.postman_collection.json` and `postman/Waypoint-Local.postman_environment.json` and select `Waypoint Local`.
3. Set `microsoftRedirectUri` to the same Chromium redirect URI configured in `MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS`.
4. Run `Microsoft Start` in `01 - Authentication`.
5. Open the returned `microsoftAuthorizationUrl` through the extension's Chrome web-auth flow and complete Microsoft sign-in/consent.
6. Read `exchange_code` from the final extension redirect URL and set `microsoftExchangeCode` in `Waypoint Local`.
7. Run `Microsoft Session Exchange`, which stores `jwt`, `waypointRefreshToken`, `userId` and `userEmail`.
8. Run `Current Session`, `Refresh Waypoint Session` and `Refresh Replay Rejected`.
9. Verify `Logout` revokes the Waypoint session but keeps the Microsoft link; sign in again with Microsoft and confirm it returns to the same Waypoint account.
10. Use `Microsoft Link Start` for explicit cross-provider linking and `Microsoft Disconnect` for explicit unlinking.

The Microsoft requests are part of the main `01 - Authentication` Postman folder and use the shared `Waypoint Local` environment. The browser consent/redirect step remains interactive because Microsoft authentication must happen in the browser/Chrome extension context.

## Production configuration notes

Production validates HTTPS callback/provider URLs, exact Chromium extension redirect URIs, short JWT/session lifetimes, Redis-backed distributed state and a mounted managed-secret file for Microsoft token encryption. Production values are intentionally environment-specific and are not hard-coded in the repository.

## Security properties

The Microsoft flow includes:

- Authorization Code + PKCE (`S256`);
- cryptographically random OAuth state;
- hashed state and exchange codes;
- pessimistic database locking for one-time state/exchange/refresh consumption;
- exact extension redirect allowlisting;
- short-lived exchange codes;
- encrypted Microsoft refresh credentials using AES-256-GCM;
- rotating Waypoint refresh sessions;
- sanitized provider failures;
- explicit cross-provider account linking;
- no Microsoft access/refresh token exposure to the extension.

## Automated tests

Run the complete suite:

```bash
mvn clean verify
```

Authentication coverage includes Google authentication plus Microsoft login, PKCE/scopes, expired/replayed state, expired/replayed exchange codes, Waypoint refresh rotation/replay rejection, persistent links across logout, explicit disconnect, prevention of automatic cross-provider email merging, explicit linking, provider refresh-token rotation/failure cleanup, sanitized failures and concurrent first login.
