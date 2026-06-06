# Runbook: Circuit Breaker Open

## Symptom
The Grafana alert `Circuit Breaker Open` fires, indicating that the Resilience4j Circuit Breaker in `gateway-service` has transitioned to the `OPEN` state. This happens when the failure rate or slow call rate of communication with the downstream `account-service` exceeds the configured thresholds.

## Underlying Root Causes
1. **Downstream service overload**: `account-service` is slow to respond or timing out.
2. **Network latency/partition**: Unusually high packet loss or network degradation between services.
3. **Internal server error**: Downstream database deadlock, high CPU load, or out-of-memory crash.

## Diagnostics
1. Query Loki for log statements from `gateway-service` containing `CallNotPermittedException`.
2. Extract the `trace_id` from the log statements.
3. Query Tempo using the `trace_id` to inspect the call hierarchy and identify where the timeout or error occurred.

## Remediation Steps
1. **Increase timeouts or thresholds**: If the failures are expected transient latency spikes, adjust `resilience4j.circuitbreaker.instances.accountService.failureRateThreshold` or `slowCallRateThreshold` upwards.
2. **Scale downstream**: Scale the `account-service` instances or increase database pool connections.
3. **Verify Retry Settings**: Ensure that retry is configured with exponential backoff & jitter to avoid thundering herd problem.
