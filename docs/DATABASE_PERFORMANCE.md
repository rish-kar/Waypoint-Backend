# Database performance discipline

The indexes in `V13__add_hot_query_indexes.sql` match the backend's measured hot query shapes rather than indexing every column.

## Hot paths

- Entitlement checks: user + subscription status + renewal/trial/end timestamps.
- Current subscription lookup: user + newest `updated_at`.
- Webhook recovery: `processing_status=RECEIVED` + oldest `last_attempt_at`.
- Admin audit browsing: newest events, optionally scoped by admin.
- Admin user browsing: newest users and last-login sorting.

## Verification

For production-like data, run `EXPLAIN (ANALYZE, BUFFERS)` for the repository queries above and verify PostgreSQL uses the intended indexes once the table is large enough for index access to be cheaper than a sequential scan.

Track query count and p95/p99 latency for entitlement, billing status, webhook recovery, and admin user listing. New repository queries on these paths should include an index review and an integration test when their SQL shape materially changes.

Do not add speculative indexes: every index increases write cost and storage.