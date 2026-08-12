# Testing Waypoint-Backend with Postman

## Files

- `Waypoint-Backend.postman_collection.json` — all backend requests and automated assertions, including authentication, subscriptions, entitlements and admin operations
- `Waypoint-Local.postman_environment.json` — local variables and generated session values

## 1. Start the backend

From the repository root:

```bash
git switch Subscription-and-Entitlement
docker compose up -d postgres
```

Before starting `WaypointBackendApplication`, add these to the same IntelliJ Run Configuration environment variables used by the backend:

```text
ADMIN_ID=<your-admin-id>
ADMIN_PASSWORD=<a-strong-password-with-at-least-16-characters>
```

There are no default admin credentials. Start `WaypointBackendApplication` from IntelliJ using Java 21 and the `dev` profile.

The backend runs at `http://localhost:8080` and PostgreSQL at `localhost:5432`.

Confirm startup before opening Postman:

```bash
curl http://localhost:8080/actuator/health/readiness
```

Expected result:

```json
{"status":"UP"}
```

## 2. Import into Postman

1. Open Postman.
2. Select **Import**.
3. Import `postman/Waypoint-Backend.postman_collection.json`.
4. Import `postman/Waypoint-Local.postman_environment.json`.
5. Select the **Waypoint Local** environment.
6. Set `adminId` and `adminPassword` to the same values as `ADMIN_ID` and `ADMIN_PASSWORD` in IntelliJ.

## 3. Run configuration checks

Run the `00 - Health and Configuration` folder.

These requests test:

- general application health;
- liveness;
- readiness and database connectivity;
- `X-Request-ID` propagation.

They do not require Google or Lemon Squeezy credentials.

## 4. Test authentication

Run the `01 - Authentication` folder.

The requests cover:

- missing request body;
- blank Google token;
- public login-route behavior;
- invalid Google token rejection;
- successful Google login;
- missing bearer token;
- invalid authorization scheme;
- malformed JWT;
- expired signed JWT.

For a real login, the running backend must use the same Google OAuth client ID as the token:

```text
GOOGLE_CLIENT_ID=<real Google OAuth client ID>
```

Paste the real Google access token into the `googleAccessToken` environment variable and run `Google Login`.

A successful request automatically saves:

- `jwt` — Waypoint bearer token;
- `userId` — Waypoint database user ID.

The `Protected Endpoint - Expired Signed JWT` request signs an expired token using `jwtSecret`. When the backend uses a different `JWT_SECRET`, set `jwtSecret` to the same value.

## 5. Test account, subscription and entitlement APIs

Run the `02 - Account and Entitlements` folder after `Google Login`.

It verifies:

- `Account Details` — `GET /api/v1/account` returns the logged-in account;
- `Current Entitlements` — `GET /api/v1/entitlements` returns the effective entitlement and feature list;
- `Current Subscription` — `GET /api/v1/subscriptions/current` returns the effective stored subscription state;
- `Free Feature Access` — `instant-tab-search` is available to both FREE and PREMIUM users;
- `Premium Feature Access` — `ai-summary` is available only when the effective entitlement plan is PREMIUM.

For a newly created user with no subscription, the expected state is:

```text
subscription plan = FREE
subscription status = INACTIVE
premium = false
instant-tab-search allowed = true
ai-summary allowed = false
```

After a paid premium subscription is activated, the subscription plan becomes `PREMIUM_MONTHLY` or `PREMIUM_ANNUAL`.

## 6. Test billing

`Billing Status` reads only the local database and can be tested after login.

The checkout requests require real Lemon Squeezy test-mode values in the backend:

```text
LEMON_SQUEEZY_API_KEY
LEMON_SQUEEZY_STORE_ID
LEMON_SQUEEZY_MONTHLY_VARIANT_ID
LEMON_SQUEEZY_ANNUAL_VARIANT_ID
```

Run either:

- `Create Monthly Checkout`
- `Create Annual Checkout`

Expected result: HTTP `200` with an HTTPS `checkoutUrl`.

## 7. Test signed webhooks locally

Run the `04 - Webhooks` folder after `Google Login`.

The Postman pre-request scripts automatically:

1. construct the Lemon Squeezy payload;
2. calculate HMAC-SHA256 using `webhookSecret`;
3. add the generated `X-Signature` header.

Run requests in this order:

1. `Invalid Signature` — expects `401`.
2. `Activate Monthly Subscription` — expects `200`.
3. `Verify Premium Entitlement` — expects `PREMIUM`.
4. `Refund Subscription` — expects `200`.
5. `Verify Free Entitlement After Refund` — expects `FREE`.

## 8. Test admin Premium Special access

Run `Google Login` first so `userId` identifies the account being modified. Then run the `05 - Admin` folder in order.

Admin requests use HTTP Basic authentication with the `adminId` and `adminPassword` Postman variables. These must exactly match the backend `ADMIN_ID` and `ADMIN_PASSWORD` environment variables.

The folder verifies:

1. invalid admin credentials are rejected with `401`;
2. the admin can inspect a Waypoint user;
3. `POST /api/v1/admin/users/{userId}/premium-special` grants Premium Special;
4. `GET /api/v1/admin/premium-special` returns the active Premium Special count and users;
5. the user's effective subscription becomes `PREMIUM_SPECIAL` with status `PREMIUM_SPECIAL`;
6. `DELETE /api/v1/admin/users/{userId}/premium-special` revokes the grant;
7. Premium Special is no longer effective after revocation.

A grant body can optionally include `validUntil`. Omitting it creates lifetime Premium Special access:

```json
{
  "reason": "Friends and family"
}
```

For time-limited access:

```json
{
  "validUntil": "2027-08-12T00:00:00Z",
  "reason": "Friends and family"
}
```

## 9. Run the collection

Use Postman's Collection Runner after setting `googleAccessToken`, `adminId`, and `adminPassword` and selecting **Waypoint Local**.

Do not run the complete collection before configuring the provider credentials needed by the real Google Login and checkout requests.
