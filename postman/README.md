# Testing Waypoint-Backend with Postman

## Files

- `Waypoint-Backend.postman_collection.json` — importable main collection
- `Waypoint-AI.postman_collection.json` — focused importable GPT-5 nano AI smoke collection
- `Waypoint-Local.postman_environment.json` — importable local environment
- `collections/Waypoint-Backend/` — Git-synced Postman source
- `environments/Waypoint Local.environment.yaml` — Git-synced environment source

## Collection layout

- `01 - Authentication` contains Google authentication, Microsoft OAuth/session flows, refresh-token rotation/replay checks and bearer-token hardening tests.
- `03 - Billing` contains only Waypoint backend billing endpoints.
- `04 - Webhooks` contains Waypoint webhook endpoint and hardening tests.
- `05 - Admin` contains only Waypoint admin API operations.
- `06 - Lemon Squeezy Test Mode` contains the direct Lemon Squeezy provider lifecycle tests and the small E2E helpers needed to compare provider state with Waypoint.
- `07 - AI` contains GPT-5 nano model discovery, browser-intent routing and page-context chat tests.

Lemon Squeezy test variables are owned by `06 - Lemon Squeezy Test Mode`; normal Waypoint folders must not overwrite the selected provider-test subscription.

## GPT-5 nano AI tests

Start the backend with:

```text
AI_OPENAI_ENABLED=true
OPENAI_API_KEY=<your-openai-api-key>
AI_OPENAI_MODEL=gpt-5-nano
```

The OpenAI API key stays on the backend and is never stored in the Postman environment or sent by the extension.

For a focused AI-only test run, import `Waypoint-AI.postman_collection.json` together with `Waypoint-Local.postman_environment.json` and run:

1. `AI - Models`
2. `AI - Intent - Group Tabs`
3. `AI - Chat - Page Context`

The Git-synced source for the same requests is under `collections/Waypoint-Backend/07 - AI/`.

## Local setup

Run the backend with the normal local configuration plus:

```text
ADMIN_ID=<your-admin-id>
ADMIN_PASSWORD=<your-admin-password>
```

`ADMIN_PASSWORD` only needs to be non-empty. There is no application character-count limit.

In Postman, set:

```text
adminId = same value as ADMIN_ID
adminPassword = same value as ADMIN_PASSWORD
webhookSecret = same value as LEMON_SQUEEZY_WEBHOOK_SECRET
```

`webhookSecret` is the long-lived signing secret, not a webhook signature. The webhook requests generate `webhookBody` and the matching HMAC-SHA256 `webhookSignature` automatically for each payload.

For Google auth, run `Google Login` once to populate `jwt`, `userId`, and `userEmail`.

## Microsoft OAuth local E2E

Microsoft OAuth is part of the main `01 - Authentication` folder and uses the same `Waypoint Local` environment.

Configure the backend with a real development Microsoft Entra app registration:

```text
MICROSOFT_CLIENT_ID=<development-client-id>
MICROSOFT_CLIENT_SECRET=<development-client-secret>
MICROSOFT_TENANT=common
MICROSOFT_CALLBACK_URL=http://localhost:8080/api/v1/auth/microsoft/callback
MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS=https://<extension-id>.chromiumapp.org/microsoft
```

Set the Postman environment variable:

```text
microsoftRedirectUri = same HTTPS chromiumapp.org URI as MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS
```

Then:

1. Run `Microsoft Start`.
2. Read `microsoftAuthorizationUrl` from the environment or Postman console and open it through the extension's `chrome.identity.launchWebAuthFlow` flow.
3. Complete Microsoft sign-in/consent.
4. The backend callback redirects to the extension URI with `waypoint_auth=success&exchange_code=...`. Copy that `exchange_code` value into `microsoftExchangeCode`.
5. Run `Microsoft Session Exchange`. It stores `jwt`, `waypointRefreshToken`, `userId`, and `userEmail` in `Waypoint Local`.
6. Run `Current Session`, then `Refresh Waypoint Session`, then `Refresh Replay Rejected`.
7. `Microsoft Link Start` is the explicit cross-provider linking test for an already authenticated Waypoint account.
8. `Logout` revokes Waypoint sessions but preserves the Microsoft account link. Sign in again with the same Microsoft identity and verify the same `userId` is returned.
9. `Microsoft Disconnect` is the explicit unlink operation and requires a currently valid Waypoint JWT.

The Microsoft sign-in/consent screen is intentionally not automated by Postman because the browser/Chrome identity redirect is part of the actual extension OAuth flow. Everything before and after that interactive provider step is covered by the main Postman collection and backend integration tests.

## Webhook hardening smoke tests

`04 - Webhooks` includes focused receiver checks in addition to normal subscription-state smoke tests:

- `Invalid Signature` -> malformed signature returns `401`.
- `Verify Invalid Signature Not Stored` -> rejected payload never reaches `webhook_events`.
- `Unsupported Signed Event` -> valid HMAC but unsupported event is acknowledged.
- `Verify Unsupported Event Ignored` -> event is stored as `IGNORED`.
- `Duplicate Idempotency` -> sends the same signed payload twice.
- `Verify Duplicate Idempotency` -> only one persisted event row exists for the identical body hash.
- `Unexpected Store Failure` -> valid HMAC from a deliberately wrong store is rejected.
- `Verify Unexpected Store Failed` -> event remains visible as `FAILED` with a safe error.

Stale `RECEIVED` recovery and `FAILED` retry reclamation require internal timing/state control, so those cases are covered by Maven integration tests rather than manual Postman mutation.

## Lemon Squeezy lifecycle tests

Run direct provider lifecycle work only under `06 - Lemon Squeezy Test Mode`.

The lifecycle collection covers paid/no-trial creation, active-state verification, cancellation, resume, pause, unpause, invoice discovery, full refund, exact Waypoint subscription synchronization and strict refund-webhook verification.

`14 - Verify Refund Webhook` filters the admin webhook store for the selected invoice and requires a matching `subscription_payment_refunded` event with `processingStatus = PROCESSED`.

## Admin management

The `05 - Admin` folder is the operational admin surface. Admin requests use HTTP Basic authentication and are separate from normal Waypoint JWT authentication.

### Read and filter data

- `Admin - Overview` — aggregate counts for users, premium users, subscriptions, active special grants and webhook processing.
- `Admin - List Users` — paged users. Supports `q`, `provider`, `plan`, `premium`, created-date and last-login filters.
- `Admin - Find User by Email` — exact email lookup.
- `Admin - List Subscriptions` — paged subscriptions. Supports user, email, provider, plan, status, external IDs and date filters.
- `Admin - List Special Grants` — all active, expired and revoked Premium Special grants with filters.
- `Admin - List Webhook Events` — paged webhook records. Raw payloads are omitted by default; use `includePayload=true` only when required.
- `Admin - List Plans` — complete local plan catalogue.
- `Admin - Audit Events` — audit trail for admin mutations.

All list endpoints default to 50 rows per page and allow at most 500 rows per request. Use `page`, `size`, `sort`, and `direction` to navigate the entire data set.

### Manage data

- `PUT /api/v1/admin/users/{userId}/premium-special` — grant or replace Premium Special access.
- `DELETE /api/v1/admin/users/{userId}/premium-special` — revoke Premium Special access.
- `PATCH /api/v1/admin/subscriptions/{subscriptionId}` — controlled internal correction of subscription status and renewal/end timestamps.
- `PATCH /api/v1/admin/webhook-events/{eventId}` — controlled correction of webhook processing metadata.

The admin API intentionally does not expose arbitrary SQL or unrestricted table mutation. Billing-provider IDs, raw plan mappings, authentication identities and schema-level data are not blindly writable through one generic endpoint.

Every admin mutation writes an `admin_audit_events` record containing the admin ID, action, resource type, resource ID, details and timestamp.

## Premium Special grant body

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

Examples:

```json
{
  "status": "ACTIVE"
}
```

```json
{
  "status": "CANCELLED",
  "endsAt": "2027-01-31T00:00:00Z"
}
```

Use `clearRenewsAt` or `clearEndsAt` to explicitly clear those timestamps.

## Webhook metadata update body

Example:

```json
{
  "processingStatus": "PROCESSED",
  "clearErrorMessage": true,
  "processedAt": "2026-08-13T12:00:00Z"
}
```

This changes stored processing metadata only. It does not replay a webhook.
