# Lemon Squeezy Checkout and Subscription Lifecycle

## Configuration

Required provider values include API key, store ID, monthly/annual variant IDs, webhook secret and API base URL. Production also enables scheduled reconciliation by default.

```text
LEMON_SQUEEZY_API_KEY
LEMON_SQUEEZY_STORE_ID
LEMON_SQUEEZY_MONTHLY_VARIANT_ID
LEMON_SQUEEZY_ANNUAL_VARIANT_ID
LEMON_SQUEEZY_WEBHOOK_SECRET
LEMON_SQUEEZY_API_BASE_URL
LEMON_SQUEEZY_RECONCILIATION_ENABLED
LEMON_SQUEEZY_RECONCILIATION_INITIAL_DELAY_MS
LEMON_SQUEEZY_RECONCILIATION_INTERVAL_MS
```

## Plans

`GET /api/v1/billing/plans` returns the customer-facing local plan catalogue. The local database remains the product/entitlement source used by the application; provider variant identifiers map local checkout plans to Lemon Squeezy variants.

## Checkout flow

```text
Authenticated user
  -> POST /api/v1/billing/checkout { plan }
  -> BillingService resolves user + configured variant
  -> checkout coordination/intent persistence
  -> Lemon Squeezy API creates hosted checkout
  -> return checkout URL
  -> user completes provider checkout
```

Provider custom data includes the internal Waypoint user identifier so later webhooks can map provider lifecycle events back to the correct account.

The codebase includes checkout-session/intent migrations to make repeated/concurrent checkout creation safer and auditable. Do not treat the redirect/checkout-return browser event as authoritative subscription activation; provider webhook/reconciliation state is the trust path.

## Webhook lifecycle

Lemon Squeezy sends subscription/payment lifecycle events to the signed webhook endpoint. After signature verification and idempotency checks, the service maps the event to the persisted subscription, normalizes status/timestamps and records processing metadata.

## Reconciliation

Production enables a scheduled reconciliation job by default. Defaults from `application-prod.yml`:

- initial delay: 60,000 ms;
- interval: 900,000 ms (15 minutes).

Reconciliation closes gaps caused by missed/delayed webhooks or provider/local divergence. It should be safe to repeat and should respect provider event ordering.

## Billing status

`GET /api/v1/billing/status` and `/subscriptions/current` read local state; they are not live Lemon Squeezy queries. This makes entitlement reads fast and independent of provider availability.

## Test mode procedure

1. Configure Lemon Squeezy test-mode store/variants/API key/webhook secret.
2. Authenticate a test Waypoint user.
3. Call `/billing/plans` and `/billing/checkout`.
4. Complete the hosted checkout in test mode.
5. Deliver/observe signed subscription webhook events.
6. Verify local subscription and entitlement changes.
7. Replay events to verify idempotency.
8. Test cancellation through future `ends_at`, expiry and refund behavior.
9. Test reconciliation after intentionally missing a webhook.

Never use production API keys/webhook secrets in committed Postman environments or automated test fixtures.
