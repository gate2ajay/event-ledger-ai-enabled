# Agentic Context Map: Event Ledger System

This file serves as a system context map and guide for AI Coding Agents (such as Claude, Copilot, Cursor, etc.) working on this repository. It documents critical architectural decisions, security boundaries, telemetry configuration, and debugging runbooks to prevent regression and accelerate development.

---

## 🧭 System Overview

The Event Ledger is a microservice application consisting of two primary Spring Boot 3 services:
1. **`gateway-service` (Port `8080`)**: Entrypoint for public clients to submit transaction events. Handles authentication, initial validation, idempotency, and propagates events downstream.
2. **`account-service` (Port `8081`)**: Internal service storing transaction ledgers, processing deposits/withdrawals, and computing account balances.

---

## 🛠️ Architectural Decisions

### 1. Double-Sided Idempotency
To prevent double-spend and duplicate transactions, idempotency is enforced at two tiers:
* **Gateway Tier:** Validates incoming `eventId` against the local gateway database (`events` table). Duplicate submissions return an HTTP status code `209 Conflict` along with the original payload.
* **Account Tier:** Enforces database-level uniqueness on the `eventId` in the `transactions` table. This serves as a safety fallback in case of race conditions that bypass the gateway level. Duplicate transactions caught here throw a constraint violation and return `209 Conflict`.

### 2. Out-of-Order Running Balance Calculation
Transactions can arrive out of order from downstream or upstream batches. 
* **Design Decision:** The system **never** persists or caches a static running balance column in the database.
* **Mechanism:** The Account Service computes balances dynamically when queried (`/accounts/{accountId}/balance`). It pulls all transactions for the specified account, sorts them chronologically by their `eventTimestamp`, and sums up credit and debit amounts.

### 3. Resilience Wrappers (Resilience4j)
All HTTP calls from the Gateway to the Account Service are wrapped inside a Resilience4j decorator chain:
* **Bulkhead:** Limits maximum concurrent connections to prevent cascading service failure.
* **Circuit Breaker:** Detects downstream service downtime. If failure rate thresholds are met, the circuit opens and throws a `503 Service Unavailable` error immediately.
* **Retry:** Retries transient timeouts or network blips using exponential backoff with random jitter.

---

## 🔐 Security & Credentials

### 1. User Authentication (Public Gateway)
* Public-facing endpoints require a JWT bearer token.
* **JWT Issuer:** `/auth/token?client=<client-name>` on port `8080`.
* **Header Format:** `Authorization: Bearer <JWT_TOKEN>`

### 2. Machine-to-Machine (M2M) Authentication (Internal Account Service)
* Direct calls to `account-service` (port `8081`) bypass JWT validation and require a shared secret.
* **Secret Key:** Injected via properties or environment variables (`services.account.m2m-secret`).
* **Default Value:** `internal-gateway-m2m-secret`
* **Header Format:** `Authorization: Bearer internal-gateway-m2m-secret`

---

## 📊 Telemetry and Observability Configuration

Traces, logs, and metrics are fully correlated and visible in Grafana:

### 1. OpenTelemetry Tracing
* Tracing is bridged using Micrometer Tracing.
* **Exporter Protocol:** HTTP/protobuf (OTLP).
* **Endpoint:** `http://tempo:4318/v1/traces` (Port `4318` is Tempo's HTTP ingest port, **not** gRPC port `4317`).
* **Environment Variable:** `OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318/v1/traces`

### 2. Structured Log Correlation (Loki)
* Logs are written in structured JSON format via `logstash-logback-encoder`.
* The MDC context is configured in `logback-spring.xml` using `%mdc{traceId}` and `%mdc{spanId}`.
* **Gotcha:** Internal trace exporter exporter threads (e.g., `OkHttp http://tempo...`) will show blank `trace_id` and `span_id` as they do not operate inside a web request context. This is expected.

---

## 🚨 Local Diagnostics & AIOps

* **Port Map:**
  - Gateway API: `http://localhost:8080`
  - Account API: `http://localhost:8081`
  - H2 Database Consoles: `/h2-console` on both service ports (using JDBC URLs `jdbc:h2:mem:gatewaydb` and `jdbc:h2:mem:accountdb` respectively).
  - Grafana Dashboard: `http://localhost:3001`
  - Prometheus: `http://localhost:9090`
  - Loki: `http://localhost:3100`
  - Mailpit UI: `http://localhost:8025`
  
* **AI Alert Webhook Listener & Self-Healing:**
  The webhook listener runs under `/aiops-agent/agent_listener.py` on port `5000` (mapped to `5005` in Grafana alerts). 
  - **Diagnostics:** When a service fails, it processes Grafana alert callbacks, pulls Loki logs, parses the Tempo trace context, and creates post-mortem diagnosis reports inside `/aiops-agent/diagnoses/`.
  - **Self-Healing Remediator:** On receiving a service-down alert, it automatically attempts to recover the service by executing a `docker compose restart <service>` command in a subprocess, restoring system health without manual intervention.
