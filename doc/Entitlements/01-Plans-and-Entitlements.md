# Plans, Subscriptions and Entitlements

## Principle

Entitlements are resolved from local durable state. A user request does not call Lemon Squeezy to decide whether a feature is unlocked.

## Plan families

The backend supports Free, paid Premium plans (monthly/annual) and `PREMIUM_SPECIAL`. Premium Special is an administrator-managed complimentary grant and is stored separately from billing subscriptions so it can be audited without creating fake provider records.

## Effective access precedence

An active, unexpired Premium Special grant takes precedence in effective entitlement resolution. If that grant is revoked or expires, resolution falls back to any valid paid subscription. If no qualifying subscription exists, the user is Free.

Subscription behavior represented by the implementation includes:

- `ACTIVE` -> Premium;
- `ON_TRIAL` -> Premium;
- `CANCELLED` with future end time -> Premium until that time;
- expired/refunded terminal state -> Free;
- unknown/malformed/no subscription -> fail safely to Free.

The exact enum/service implementation is authoritative if new provider statuses are added.

## Feature access

Free currently exposes only the base `instant-tab-search` entitlement. Premium plans and active Premium Special unlock the full premium feature set represented by the local plan catalogue.

Endpoints:

```text
GET /api/v1/entitlements
GET /api/v1/entitlements/features/{feature}
```

Feature checks should use stable feature identifiers shared with the frontend contract. Unknown features should not accidentally grant access.

## Premium Special lifecycle

Admin endpoints allow grant/replace and revoke operations. A grant may be lifetime or time-limited depending on request fields. Mutation actor/timestamps are audited.

## Data consistency

Provider subscription state can change through webhook, reconciliation or controlled admin correction. Entitlement resolution must produce the same result regardless of which legitimate path updated the subscription row.

## Testing matrix

Test at least:

- no subscription/no grant;
- active monthly;
- active annual;
- trial;
- cancelled before end date;
- cancelled after end date;
- expired;
- refunded;
- active Premium Special with no paid plan;
- active Premium Special overriding non-premium provider state;
- expired/revoked Premium Special falling back to valid paid plan;
- unknown feature ID.
