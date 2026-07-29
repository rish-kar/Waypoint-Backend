# Testing Waypoint-Backend with Postman

## Files

- `Waypoint-Backend.postman_collection.json` — requests and automated assertions
- `Waypoint-Local.postman_environment.json` — local variables and generated session values

## 1. Start the backend

From the repository root:

```bash
git checkout project-configuration
docker compose up --build
```

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

## 3. Run configuration checks

Run the `00 - Health and Configuration` folder.

These requests test:

- general application health;
- liveness;
- readiness and database connectivity;
- `X-Request-ID` propagation.

They do not require Google or Lemon Squeezy credentials.

## 4. Test authentication

For a real login, the running backend must use the same Google OAuth client ID as the token:

```text
GOOGLE_CLIENT_ID=<real Google OAuth client ID>
```

Obtain a Google access token using the Waypoint extension's existing Google sign-in flow, then paste it into the Postman environment variable:

```text
googleAccessToken
```

Run `01 - Authentication > Google Login`.

A successful request automatically saves:

- `jwt` — Waypoint bearer token;
- `userId` — Waypoint database user ID.

The other authentication requests verify missing-token validation and unauthenticated endpoint protection.

## 5. Test account and entitlement APIs

Run the `02 - Account and Entitlements` folder after Google Login.

It verifies:

- `/api/v1/me` returns the logged-in user;
- `/api/v1/entitlements` returns a valid plan and feature list.

A newly created user should initially receive the `FREE` plan with `instant-tab-search`.

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

Run the `04 - Webhooks` folder after Google Login.

The Postman pre-request scripts automatically:

1. construct the Lemon Squeezy payload;
2. calculate HMAC-SHA256 using `webhookSecret`;
3. add the generated `X-Signature` header.

The environment defaults match the development profile:

```text
webhookSecret=local-webhook-secret
monthlyVariantId=local-monthly-variant-id
```

Change these variables when the backend is started with different values.

Run requests in this order:

1. `Invalid Signature` — expects `401`.
2. `Activate Monthly Subscription` — expects `200`.
3. `Verify Premium Entitlement` — expects `PREMIUM`.
4. `Refund Subscription` — expects `200`.
5. `Verify Free Entitlement After Refund` — expects `FREE`.

The webhook simulation does not call Lemon Squeezy. It tests signature verification, webhook persistence, subscription updates and entitlement calculation against the local database.

## 8. Run the collection

Use Postman's Collection Runner after setting `googleAccessToken` and selecting **Waypoint Local**.

Do not run the entire collection before configuring real Google and Lemon Squeezy credentials because the Google Login and checkout requests are real provider integrations.
