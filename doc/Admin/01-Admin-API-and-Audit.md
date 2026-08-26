# Admin API, Audit and Operations

## Purpose

The admin API is a typed operational management surface, not a raw SQL/database console. It supports inspection, controlled corrections, Premium Special management, provider-event diagnosis and administrator-account management.

## Authentication/authorization

Admin endpoints use separate HTTP Basic authentication. Production adds TOTP. `ADMIN` can inspect normal admin data; `SUPER_ADMIN` is required for all mutations and all `/admin/accounts/**` operations.

## Read operations

Admin reads provide overview counts and paginated/filterable access to users, subscriptions, special grants, webhook events and audit events. Filters include IDs, email/provider/plan/status, date ranges and other domain-specific fields. Default page size in `AdminController` is 50.

Webhook list calls default `includePayload=false`, reducing accidental sensitive-data exposure.

## Controlled mutations

Supported mutation classes include:

- correcting allowed subscription lifecycle/timestamp fields;
- granting/replacing Premium Special;
- revoking Premium Special;
- correcting webhook processing metadata;
- creating/updating administrator accounts.

Each mutation should validate domain invariants and write an admin audit event with actor, action and resource identity.

## Account roles

Admin-account endpoints are isolated under `/api/v1/admin/accounts`. Account passwords use Spring's delegating password encoder. Avoid sharing one operational admin credential across people; individual accounts improve accountability.

## Audit trail

`GET /api/v1/admin/audit-events` supports filters for admin ID, action, resource type/resource ID and time range. Audit rows should be append-oriented; correcting a business record should not erase the history of who changed it.

## Operational use procedure

1. Query the user/subscription/webhook record first.
2. Correlate request ID/provider external IDs and event timestamps.
3. Prefer replay/reconciliation when it can safely reconstruct provider truth.
4. Use an admin mutation only for a documented correction case.
5. Re-read effective entitlement after mutation.
6. Verify the audit event exists.
7. Never paste Basic password, TOTP secret/code, JWT or webhook secret into ticket text.

## Why there is no raw DB endpoint

Direct arbitrary SQL over HTTP would bypass domain validation, roles and auditing. If an operational capability is repeatedly needed, add a typed, validated admin operation and tests instead.
