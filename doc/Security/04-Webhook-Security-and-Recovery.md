# Webhook Security, Idempotency and Recovery

## Endpoint

```text
POST /api/v1/webhooks/lemonsqueezy
X-Signature: <provider signature>
```

This endpoint is public at the Spring Security layer because Lemon Squeezy cannot present a Waypoint JWT. Authentication is instead the webhook HMAC signature.

## Signature verification

The backend verifies `X-Signature` using the configured `LEMON_SQUEEZY_WEBHOOK_SECRET` and HMAC-SHA256. Comparison must be constant-time to avoid timing leakage. The raw request bytes used for signature verification must not be modified before validation.

## Idempotency

Webhook events are persisted so retries do not blindly replay business mutations. The codebase has evolved beyond simple one-shot processing: migrations add attempt tracking, provider event timestamps and payload redaction. Event processing state allows operators to distinguish received, processed and failed/recoverable work.

Provider event time is important because delivery order is not guaranteed. Subscription state updates should reject or safely handle stale out-of-order lifecycle events rather than moving local state backwards.

## Payload retention/redaction

Webhook payloads can contain provider/customer metadata. Migration V11 introduces payload redaction behavior. Admin list APIs only include payloads when explicitly requested. Do not emit raw webhook bodies to application logs.

## Retry/recovery

Processing code records attempts/status so failures can be inspected through admin webhook endpoints and recovery logic. A network or database failure after receipt should not require manually editing subscription rows without an audit trail.

## Refund/subscription mapping

Only events supported by the implemented mapping should be subscribed to. The service must identify the local user/subscription from trusted provider/custom data and persisted external identifiers. Unknown/malformed events should fail safely and remain diagnosable.

## Rate and size protection

The webhook route has a production Redis rate bucket of 120 requests/minute per remote address and is subject to the 1 MiB API request-body limit.

## Testing procedure

1. Generate a JSON test payload.
2. Compute HMAC-SHA256 using the exact raw bytes and configured test secret.
3. Verify valid signature succeeds.
4. Change one byte without recomputing signature; expect rejection.
5. Replay the exact event; verify idempotent outcome.
6. Deliver older/newer lifecycle events out of order and verify local state monotonicity rules.
7. Simulate a processing failure and verify attempt/error state is recorded/recoverable.
8. Confirm logs never contain signature secret or full raw sensitive payload.
