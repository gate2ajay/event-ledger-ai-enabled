# Runbook: Duplicate Event

## Symptom
A high frequency of `DuplicateEventException` or `DuplicateTransactionException` is observed in the service logs, indicating a storm of duplicate transaction submissions.

## Underlying Root Causes
1. **Client Retry Storm**: An upstream client is retrying successful requests because it timed out waiting for the HTTP response.
2. **Missing Idempotency Key**: The upstream client is submitting different events with the same `eventId` (key collision).

## Diagnostics
1. Scan Loki logs for `Duplicate event detected` or `Duplicate transaction detected`.
2. Extract the `eventId` from the log message.
3. Compare the body of the duplicate event with the original event using H2 DB console or by tracing in Loki. If they differ, it is a key collision; if they are identical, it is a network retry.

## Remediation Steps
1. **Configure Client Retries**: Ensure clients use exponential backoff and jitter for retries.
2. **Shorten Gateway Timeouts**: If the gateway is taking too long to process (causing client timeouts), optimize processing time or increase the client-side timeout value.
3. **Verify Gateway Cache**: If using an in-memory or Redis-based idempotency cache, ensure its TTL (Time-To-Live) is long enough to cover the retry window of upstream clients.
