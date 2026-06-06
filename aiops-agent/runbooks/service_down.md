# Runbook: Service Down

## Symptom
The Prometheus alert `Gateway Service is Down` or `Account Service is Down` is triggered because the `up` metric equals 0.

## Underlying Root Causes
1. **JVM Crash (OOM)**: The microservice JVM ran out of memory and crashed.
2. **Container Exit**: The Docker container stopped or crashed due to an external signal or fatal startup configuration error.
3. **Health Check Failure**: The `/actuator/health` endpoint returned HTTP 5xx or timed out, causing Docker's healthcheck to fail and the container to be restarted (if restart policy is always) or taken offline.

## Diagnostics
1. Inspect container logs before the crash. Promtail aggregates these to Loki.
2. Search Loki logs for keywords: `OutOfMemoryError`, `NullPointerException`, `FATAL`, `ERROR`.
3. Check the JVM heap dump at `/var/log/dumps` (mapped to host `./dumps/gateway` or `./dumps/account`).

## Remediation Steps
1. **Analyze Heap Dump**: Open the generated `.hprof` file in Eclipse Memory Analyzer (MAT) or visualvm to locate the leak.
2. **Tune JVM memory parameters**: Adjust `-Xmx` or `-Xms` values in the container startup commands.
3. **Enable Auto-Healing**: Verify that the container's `restart: always` policy is configured in `docker-compose.yml`.
