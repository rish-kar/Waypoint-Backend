# Testing Waypoint-Backend with Postman

The Git-synced Postman source is the single source of truth:

- `collections/Waypoint-Backend/` — collection used by the connected Postman workspace
- `environments/Waypoint Local.environment.yaml` — connected environment source
- `Waypoint-Local.postman_environment.json` — optional environment export
- `Waypoint-AI.postman_collection.json` — optional focused AI-only export

The old generated main collection JSON has been removed because the workspace reads the Git-synced collection directly.

## Collection flow

Run the folders in order where applicable.

### 00 - Health and Configuration

1. Health
2. Liveness
3. Readiness

### 01 - Authentication

Authentication is split by flow.

**01 - Google**
1. Start Login — asks the Waypoint backend to create the Google authorization URL and one-time exchange handle
2. Open `googleAuthorizationUrl` in a browser and complete Google sign-in
3. Complete Login — exchanges the one-time handle for the Waypoint JWT and refresh token

**02 - Microsoft Login**
1. Start Login
2. Exchange Session
3. Refresh Token — optional manual rotation test

**03 - Session**
1. Current Session
2. Logout

**04 - Microsoft Account**
1. Link Account
2. Disconnect Account

Google Complete Login and Microsoft Exchange Session populate the shared Waypoint session variables such as `jwt`, `waypointRefreshToken`, `userId`, and `userEmail`.

The access JWT remains intentionally short-lived. For normal Bearer-authenticated Postman requests, the collection now detects an expiring JWT and automatically calls `/api/v1/auth/session/refresh`, rotates `waypointRefreshToken`, stores the new `jwt`, and sends the original request with the refreshed token. You should not need to sign in again every 15 minutes while the refresh token is still valid.

### 02 - Account and Entitlements

1. Account Details
2. Current Entitlements
3. Current Subscription
4. Instant Tab Search Access
5. AI Summary Access

### 03 - Billing

1. Available Plans
2. Billing Status
3. Checkout
   - Monthly Checkout
   - Annual Checkout

Choose one checkout branch.

### 04 - Webhooks

**01 - Subscription Events**
1. Activate Monthly Subscription
2. Refund Subscription

These requests are only for local/manual subscription-event testing. Invalid-signature, duplicate-delivery and other hardening cases are covered by automated backend tests instead of the operational Postman collection.

### 05 - Admin

Admin requests use HTTP Basic authentication generated from the selected environment values:

```text
adminId
adminPassword
```

The generated Base64 value is stored in:

```text
adminBasicAuth
```

Production admin requests also require Microsoft Authenticator TOTP. Configure the same Base32 `ADMIN_TOTP_SECRET` in Microsoft Authenticator as a standard OATH-TOTP account using SHA-1, 6 digits and the normal 30-second period. Put the currently displayed code in:

```text
adminTotp
```

The Postman collection automatically sends it as `X-Admin-TOTP` for admin requests. Microsoft Authenticator will continue to display a new code every 30 seconds, but the backend accepts the entered code for up to five minutes.

**01 - Overview**
- Overall admin summary

**02 - Users**
1. List Users
2. Find User by Email — stores the returned ID in `userId`
3. Get User

**03 - Subscriptions**
1. List Subscriptions — stores the selected subscription in `adminSubscriptionId`
2. Update Subscription

**04 - Premium Special**
1. Grant Premium Special
2. Premium Special Count
3. List Special Grants
4. Get Special Grant
5. Revoke Premium Special

**05 - Webhook Events**
1. List Webhook Events
2. Get Webhook Event
3. Update Webhook Event

**06 - Plans**
1. List Plans

**07 - Audit**
1. Audit Events

### 06 - Lemon Squeezy Test Mode

Provider testing is split into clear branches.

**01 - Setup**
1. Bootstrap Test Context
2. Retrieve Provider Subscription
3. Create Paid No-Trial Checkout
4. Capture New Paid Subscription
5. Verify New Active Subscription

**02 - Lifecycle**
1. Cancel Paid Subscription
2. Resume Paid Subscription
3. Pause Subscription
4. Verify Waypoint Sync State
5. Unpause Subscription

**03 - Refund**
1. List Subscription Invoices
2. Refund Selected Invoice
3. Verify Refund Sync
4. Verify Refund Webhook

**04 - Recovery**
1. Recover Lifecycle Subscription

Use Recovery only when a lifecycle test was interrupted and the selected Lemon Squeezy subscription needs to be restored into the environment.

### 07 - AI

1. Models
2. Usage
3. Intent - Group Tabs
4. Chat - Page Context

## Local setup

Start the backend with the normal local configuration. For admin requests configure matching values in the backend and Postman environment:

```text
ADMIN_ID=<your-admin-id>
ADMIN_PASSWORD=<your-admin-password>
```

Postman environment:

```text
adminId = same value as ADMIN_ID
adminPassword = same value as ADMIN_PASSWORD
adminTotp = current Microsoft Authenticator code when testing production
webhookSecret = same value as LEMON_SQUEEZY_WEBHOOK_SECRET
```

## Google OAuth local flow

Google OAuth is backend-controlled. Keep these values only in the backend/IntelliJ run configuration:

```text
GOOGLE_CLIENT_ID=<google-web-client-id>
GOOGLE_CLIENT_SECRET=<google-web-client-secret>
APP_BASE_URL=http://localhost:8080
```

Google Cloud must authorize the backend callback once:

```text
http://localhost:8080/api/v1/auth/google/callback
```

Postman does **not** need the Google client ID, client secret, authorization code, access token, or Google refresh token.

Run:

1. Authentication → Google → `01 - Start Login`.
2. Open the generated `googleAuthorizationUrl` in a browser and sign in with Google.
3. The browser returns to the Waypoint backend callback and shows that sign-in is complete.
4. Run `02 - Complete Login`.
5. Postman stores the Waypoint `jwt`, `waypointRefreshToken`, `userId`, and `userEmail`.

The only Google-specific Postman variables are `googleAuthorizationUrl` and `googleExchangeCode`, and both are generated automatically by the backend flow rather than configured by you.

## Microsoft OAuth local flow

Configure the backend with the development Microsoft Entra values and set `microsoftRedirectUri` in Postman to the same allowed Chromium extension redirect URI.

Then run:

1. Authentication → Microsoft Login → Start Login
2. Open `microsoftAuthorizationUrl` through the extension web-auth flow
3. Complete Microsoft sign-in
4. Copy the returned `exchange_code` into `microsoftExchangeCode`
5. Run Exchange Session
6. `Refresh Token` is only needed when you specifically want to test refresh-token rotation manually; ordinary Bearer requests auto-refresh the session.

## Premium Special body

Lifetime:

```json
{
  "reason": "Friends and family"
}
```

Time-limited:

```json
{
  "validUntil": "2027-08-12T00:00:00Z",
  "reason": "Friends and family"
}
```

## Subscription update body

Example:

```json
{
  "status": "ACTIVE"
}
```

Or:

```json
{
  "status": "CANCELLED",
  "endsAt": "2027-01-31T00:00:00Z"
}
```

Use `clearRenewsAt` or `clearEndsAt` when those timestamps must be cleared.
