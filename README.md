# Event Ledger System (AI-Augmented Implementation)

An **Event Ledger** microservices system built using **Spring Boot 3.x**, **Gradle** (multi-module), and **Docker Compose**. It incorporates robust distributed transaction handling, resilience engineering, distributed tracing, and an intelligent AIOps diagnostic agent.

---

## 🚀 Port Directory

* **Gateway Service**: `http://localhost:8080`
* **Account Service**: `http://localhost:8081`
* **Grafana (Admin/Admin)**: `http://localhost:3000`
* **Mailpit (Local Alert Inbox)**: `http://localhost:8025`
* **Prometheus (Metrics)**: `http://localhost:9090`
* **Loki (Logs)**: `http://localhost:3100`
* **Tempo (Traces)**: `http://localhost:3200`
* **AIOps Agent (Webhook)**: `http://localhost:5000`

---

## 🛠️ Crucial Implementation Details

### 1. Double-Sided Idempotency
- **Gateway Level**: Checks incoming Event IDs against local database records. Returns `209 Conflict` (idempotency hit) with the original response body if duplicates are detected.
- **Account Level**: Enforces unique database constraints on `eventId` for transactions. If duplicate transaction requests bypass the gateway (due to concurrent race conditions), the database throws a constraint violation, caught by the exception handler to return `209 Conflict`.

### 2. Chronological Out-of-Order Balance Calculation
- Downstream events can arrive out of order.
- The `account-service` persists transactions with their original `eventTimestamp`.
- When calculating account balances dynamically, it pulls all transactions for the account, sorts them chronologically by `eventTimestamp`, and computes the running balance as `Credits - Debits`.

### 3. Integrated Resilience (Resilience4j)
Calls from `gateway-service` to `account-service` pass through a combined Resilience4j stack:
- **Bulkhead**: Limits concurrent calls to isolate downstream resource exhaustions.
- **Circuit Breaker**: Detects downstream outages and fails fast with `503 Service Unavailable` once failure rate thresholds are hit.
- **Retry with Exponential Backoff & Jitter**: Re-attempts transient failures with random backoffs to avoid overloading the downstream service.

### 4. Telemetry Stack & Observability
- Spring Boot 3 Micrometer Tracing coupled with OpenTelemetry bridges trace propagation.
- **Logback JSON output** automatically injects `trace_id` and `span_id` from MDC context.
- **Grafana Integration**: Cross-links Loki logs to Tempo traces (via trace IDs parsed from log JSON) and links Tempo traces back to Loki logs for highly efficient microservice debugging.

---

## 📦 How to Run

### 1. Run Unit and Integration Tests
To compile the modules and run all JUnit integration tests (using RestAssured):
```bash
./gradlew test
```

### 2. Build and Launch Containerized Stack
First, build the Spring Boot executable Jars:
```bash
./gradlew bootJar -x test
```
Then, spin up the entire telemetry stack and microservices using Docker Compose:
```bash
docker compose up --build
```

### 3. Run and Verify the AIOps Webhook Agent
The `/aiops-agent` listener operates webhook processing and diagnostic report generation.
To trigger a mock Circuit Breaker alert locally and check the generated post-mortem diagnosis report:
```bash
python3 aiops-agent/agent_listener.py --mock circuit
```
The resulting markdown report detailing the Loki log scope, Tempo trace context, matched runbook, and k6 stress-test recommendation will be created under `aiops-agent/diagnoses/`.

To run the live webhook server for Grafana alert callbacks:
```bash
python3 aiops-agent/agent_listener.py --port 5000
```
