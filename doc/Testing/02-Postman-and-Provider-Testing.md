# Postman and Provider Integration Testing

## Generated collection

The repository contains `postman/Waypoint-Backend.postman_collection.json` and a synchronization script. CI installs PyYAML 6.0.2, runs:

```bash
python scripts/sync_postman_collection.py
```

and then fails if the generated collection differs from Git. This makes the maintained source definition and committed collection a contract rather than a hand-edited artifact.

When an endpoint changes, update the collection source/generator inputs and regenerate; do not patch only the generated JSON.

## Environment handling

Keep secrets out of the committed collection. Store local/test environment values in untracked Postman environments or your secret manager.

Common variables include backend base URL, Google test access token, Waypoint JWT, admin credentials/TOTP code and Lemon Squeezy test values.

## Google test flow

1. Configure the backend with a Google OAuth client ID intended for the test client.
2. Obtain a valid Google access token for a test account.
3. Call `POST /api/v1/auth/google`.
4. Store the returned Waypoint JWT in a local Postman environment.
5. Call `/account`, `/billing/plans`, `/subscriptions/current` and `/entitlements` with Bearer auth.
6. Repeat with a token for the wrong OAuth audience and verify rejection.

Never publish reusable provider access tokens in Postman examples.

## Lemon Squeezy test flow

1. Use a test-mode store/API key/variants/webhook secret.
2. Authenticate a test Waypoint user.
3. Create checkout through `/billing/checkout`.
4. Complete provider checkout.
5. Deliver/observe signed webhook events.
6. Verify local billing/subscription/entitlement state.
7. Replay the event and test cancellation/refund/expiry paths.

For manual webhook requests, HMAC must be computed from the exact raw request body. Reformatting JSON after signing changes the bytes and should invalidate the signature.

## Admin testing

Use test-only admin accounts. In production-profile smoke tests, supply Basic auth and a current `X-Admin-TOTP`. Verify an `ADMIN` cannot mutate and a `SUPER_ADMIN` can perform an allowed mutation with a resulting audit event.
