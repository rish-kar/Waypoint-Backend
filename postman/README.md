# Testing Waypoint-Backend with Postman

## Files

- `Waypoint-Backend.postman_collection.json` — importable collection
- `Waypoint-Local.postman_environment.json` — importable local environment
- `collections/Waypoint-Backend/` — Git-synced Postman source
- `environments/Waypoint Local.environment.yaml` — Git-synced environment source

## Collection layout

- `03 - Billing` contains only Waypoint backend billing endpoints.
- `04 - Webhooks` contains Waypoint webhook endpoint tests.
- `05 - Admin` contains only Waypoint admin API operations.
- `06 - Lemon Squeezy Test Mode` contains the direct Lemon Squeezy provider lifecycle tests and the small E2E helpers needed to compare provider state with Waypoint.

Lemon Squeezy test variables are owned by `06 - Lemon Squeezy Test Mode`; normal Waypoint folders must not overwrite the selected provider-test subscription.

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
```

Run `Google Login` once to populate `jwt`, `userId`, and `userEmail`.

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
