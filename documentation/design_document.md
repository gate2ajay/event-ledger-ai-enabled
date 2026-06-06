# Design Document: Event Ledger System

This document specifies the technical design, API contracts, database schemas, security flows, telemetry protocols, and resiliency parameters for the **Event Ledger** microservice system.

---

## 1. System Architecture Diagram

Architecture diagram

![Architecture Diagram](./architecture-diagram.jpeg)

### 1.1 Key Integration Points

- **External Client to Event Gateway:** 
  Clients authenticate via JWT bearer tokens and submit financial transaction events to the Event Gateway Service (`gateway-service`) on port `8080`.
  
- **Gateway to Account Service (Internal Machine-to-Machine):** 
  The Gateway propagates transaction requests internally to the Account Service (`account-service`) on port `8081`. This communication is secured using a shared bearer token (`internal-gateway-m2m-secret`) and includes W3C `traceparent` headers for distributed trace propagation across service boundaries.
  
- **Service Databases:** 
  Each microservice integrates with its own separate, in-memory H2 database (`gatewaydb` and `accountdb`) to store domain-specific entities (idempotency records for events, and transaction ledger details for accounts).
  
- **Observability and Telemetry Stack:**
  - **Traces:** Both services push span telemetry to **Grafana Tempo** over OTLP HTTP/protobuf on port `4318/v1/traces`.
  - **Metrics:** **Prometheus** scrapes application performance metrics from Spring Boot `/actuator/prometheus` endpoints on port `9090`.
  - **Logs:** **Promtail** collects stdout JSON logs from the service containers and ships them to **Grafana Loki** on port `3100`.
  - **Unified Dashboard:** **Grafana** integrates Loki, Tempo, and Prometheus data sources on port `3001` to correlate trace IDs, logs, and system metrics.

  Sequence diagram

![Sequence Diagram](./sequence-diagram.jpeg)

---

## 2. API Specifications

### 2.1. Event Gateway API (Public-Facing)
Exposed on Port `8080`.

#### **POST `/events`**
Submits a financial transaction event. Requires a valid JWT bearer token.
* **Headers**:
  * `Authorization: Bearer <JWT_TOKEN>`
* **Request Body (JSON)**:
  ```json
  {
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {
      "source": "mainframe-batch",
      "batchId": "B-9042"
    }
  }
  ```
* **Response Status Codes**:
  * `201 Created`: Successfully processed and propagated.
  * `209 Conflict` (or `200 OK` with payload): Duplicate request (`eventId` matches existing). Returns original event payload.
  * `400 Bad Request`: Validation failure (negative amount, missing fields, invalid type).
  * `401 Unauthorized`: Missing or invalid JWT token.
  * `503 Service Unavailable`: Downstream Account Service is down or Circuit Breaker is open.

#### **GET `/events/{id}`**
Retrieves a single event by its ID from the Gateway's local database. Does not depend on the Account Service.
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Response Codes**: `200 OK`, `404 Not Found`.

#### **GET `/events?account={accountId}`**
Lists events for an account, ordered chronologically by `eventTimestamp`. Does not depend on the Account Service.
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Response Codes**: `200 OK` (list of events).

---

### 2.2. Account Service API (Internal)
Exposed on Port `8081`.

#### **POST `/accounts/{accountId}/transactions`**
Applies a transaction to the account. Enforces double-sided idempotency using `eventId`.
* **Headers**:
  * `Authorization: Bearer internal-gateway-m2m-secret`
  * `traceparent: 00-traceId-spanId-traceFlags`
* **Request Body (JSON)**:
  ```json
  {
    "eventId": "evt-001",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }
  ```
* **Response Codes**:
  * `201 Created`: Transaction saved.
  * `209 Conflict`: Duplicate transaction ID. Returns original status.
  * `401 Unauthorized`: Invalid/missing M2M security token.

#### **GET `/accounts/{accountId}/balance`**
Retrieves the balance for an account. Computed dynamically: `SUM(credits) - SUM(debits)`.
* **Headers**: `Authorization: Bearer internal-gateway-m2m-secret`
* **Response Body (JSON)**:
  ```json
  {
    "accountId": "acct-123",
    "balance": 150.00,
    "currency": "USD",
    "lastUpdated": "2026-05-15T14:02:11Z"
  }
  ```

---

## 3. Database Schemas

### 3.1. Gateway Database Schema
Each service has its own dedicated in-memory **H2 database**.

#### Table: `events`
* `event_id` (VARCHAR(50), PRIMARY KEY): The unique event identifier.
* `account_id` (VARCHAR(50), NOT NULL): The targeted account.
* `type` (VARCHAR(10), NOT NULL): Either `CREDIT` or `DEBIT`.
* `amount` (DECIMAL(15, 2), NOT NULL): Greater than `0.00`.
* `currency` (VARCHAR(3), NOT NULL): ISO currency code.
* `event_timestamp` (TIMESTAMP, NOT NULL): Chronological occurrence time.
* `metadata_json` (VARCHAR(1000), NULL): JSON-serialized string of metadata.
* `status` (VARCHAR(20), NOT NULL): `PENDING`, `COMPLETED`, or `FAILED`.

### 3.2. Account Database Schema

#### Table: `transactions`
* `event_id` (VARCHAR(50), PRIMARY KEY): Used to enforce database-level double-sided idempotency.
* `account_id` (VARCHAR(50), NOT NULL): Index on this column for quick balance queries.
* `type` (VARCHAR(10), NOT NULL): `CREDIT` or `DEBIT`.
* `amount` (DECIMAL(15, 2), NOT NULL).
* `currency` (VARCHAR(3), NOT NULL).
* `event_timestamp` (TIMESTAMP, NOT NULL).

---

## 4. Telemetry and Trace Propagation

The system utilizes the **W3C Trace Context** standard for trace propagation:
* **Header Name**: `traceparent`
* **Format**: `00-{trace_id}-{parent_id}-{trace_flags}`
  * e.g., `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`

### Execution Path tracing:
1. **Edge Request**: Client calls `/events`. The Gateway intercepts the request, extracts/creates a `traceparent`, and pushes it to MDC (Logged as `trace_id`).
2. **Propagated Call**: The Gateway attaches `traceparent` as an HTTP header in its request to the Account Service.
3. **Internal Log**: Account Service reads `traceparent`, assigns the same `trace_id` to its MDC context, and logs all transactional statements with it.

---

## 5. Resiliency Rules and Configurations (Resilience4j)

The `gateway-service` implements three nested Resilience4j decorators on calls to the `account-service`:

### 5.1. Timeout
* **Value**: `2000ms` (2 seconds). Any REST call exceeding this duration throws a `TimeoutException`.

### 5.2. Retry
* **Max Attempts**: `3` (1 initial attempt + 2 retries).
* **Backoff Strategy**: Exponential backoff with random jitter.
  * Base interval: `100ms`
  * Multiplier: `2.0`
  * Max backoff: `1000ms`

### 5.3. Circuit Breaker
* **Sliding Window Type**: Count-based.
* **Sliding Window Size**: `10` requests.
* **Failure Rate Threshold**: `50%`. If 5 out of 10 requests fail (or time out), the circuit opens.
* **Slow Call Rate Threshold**: `50%` with duration > `1000ms`.
* **Wait Duration in Open State**: `10000ms` (10 seconds) before transitioning to Half-Open.
* **Permitted Number of Calls in Half-Open**: `3` trials.

When the circuit breaker is **OPEN**, subsequent requests to downstream components are blocked and fail fast. The client receives an immediate `503 Service Unavailable` response containing the following structure:
```json
{
  "type": "about:blank",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "Account Service is currently unavailable (Circuit Breaker open)",
  "instance": "/events",
  "trace_id": "b6e8a0b6332a39a7064621a985a4298e"
}
```

### 5.4. Bulkhead
* **Type**: Semaphore or Threadpool (ThreadPool bulkhead is preferred for RestClient isolation).
* **Max Concurrent Calls**: `10`.
* **Max Queue Capacity**: `5`. If more than 15 requests are concurrently handled, additional requests fail fast with `BulkheadFullException`.

### 5.5. Central Exception Handling
The application implements centralized exception mapping via `@RestControllerAdvice` in the [GlobalExceptionHandler](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/gateway-service/src/main/java/com/ledger/gateway/exception/GlobalExceptionHandler.java).
* **RFC 7807 Problem Details:** Exception mappings return standard Spring Boot 3 `ProblemDetail` structures to provide readable, structured metadata for API clients.
* **Telemetry Propagation:** Every error payload includes a correlated `trace_id` retrieved dynamically from the active OpenTelemetry span/MDC context, simplifying request correlation.
* **Status Code Mapping:**
  * `DuplicateEventException` $\rightarrow$ HTTP `209 Conflict` (returns original payload)
  * `CallNotPermittedException` (Circuit Breaker) $\rightarrow$ HTTP `503 Service Unavailable`
  * `BulkheadFullException` $\rightarrow$ HTTP `429 Too Many Requests`
  * `TimeoutException` $\rightarrow$ HTTP `504 Gateway Timeout`
  * `MethodArgumentNotValidException` (Validation) $\rightarrow$ HTTP `400 Bad Request`
  * `Exception` (Generic Fallback) $\rightarrow$ HTTP `500 Internal Server Error`

### 5.6. Aspect-Oriented Programming (AOP) Implementation
AOP is configured to inject cross-cutting concerns (auditing and metrics tracking) dynamically without polluting core service logic:
* **Execution Monitoring ([TrackExecutionTimeAspect](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/common/src/main/java/com/ledger/common/aop/TrackExecutionTimeAspect.java)):**
  Intercepts methods annotated with `@TrackExecutionTime`. It logs the method execution time in JSON format and registers a timer metric inside the Prometheus `MeterRegistry` for visual dashboard monitoring.
* **Transaction Auditing ([AuditedTransactionAspect](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/common/src/main/java/com/ledger/common/aop/AuditedTransactionAspect.java)):**
  Intercepts methods annotated with `@AuditedTransaction`. It inspects method parameters (using reflection) to extract transaction details like `eventId`, `accountId`, and `amount`, writing a structured JSON audit log before execution begins.

---

## 6. Additional Features

The following updates were made to the event-ledger application and deployment configuration:

1. **OpenTelemetry Telemetry Alignment:** 
   Updated trace exporting in `docker-compose.yml` to target Tempo's HTTP/protobuf receiver endpoint (`http://tempo:4318/v1/traces`) instead of the gRPC receiver port. This solved the OTLP exporter connection reset errors and enabled trace ID/span ID propagation across distributed microservice logs.
2. **H2 Console Accessibility:**
   Adjusted security filter configurations in both `gateway-service` and `account-service` to bypass JWT/M2M authentication for the H2 database web consoles (`/h2-console/**`) and enabled `frameOptions.sameOrigin()` to permit nested H2 console layout loading in web browsers.
3. **Structured Log Context Mappings:**
   Validated correct MDC context extraction (`traceId` and `spanId`) mapping into JSON logger formats to ensure Loki correctly indexes and cross-references logs to Tempo traces.
4. **Developer/Agent Integration Enhancements:**
   - Introduced `AGENTS.md` context map in the root workspace directory for future assistant integration.
   - Featured parallelized cached builds and automatic health checks inside `./start.sh`.

---

## 7. Code Coverage Report

The instruction coverage of the two main services has been improved past the 80% threshold using JUnit 5, Mockito, and MockMvc to test Lombok models, security validation, and resilience fallback handlers:

| Subproject | Missed Instructions | Covered Instructions | Total Instructions | Final Coverage % |
| :--- | :--- | :--- | :--- | :--- |
| **`account-service`** | 122 | 1059 | 1181 | **89.67%** |
| **`gateway-service`** | 138 | 1161 | 1299 | **89.38%** |

### 7.1. Integration and Performance (k6) Testing

To validate system reliability, security, and high concurrency under load, two testing suites were executed:

1. **Gradle Integration Suite**:
   - Total of 23 test suites executed successfully across `gateway-service` and `account-service`.
   - Verified core workflows: JWT/M2M authentication boundaries, out-of-order running balance calculations, unique constraints fallback, and Resilience4j circuit breakers, timeouts, and bulkhead exceptions.

2. **k6 Load Performance Testing**:
   - Executed E2E request flow: token generation $\rightarrow$ event post $\rightarrow$ duplicate idempotency checks.
   - Tested under ramping concurrency up to 10 Virtual Users (VUs) for 20 seconds.
   - **Performance Results**:
     - **Total Requests**: 2,255 requests (112 req/sec).
     - **Request Failure Rate**: 0.00% (0 failures out of 2,255).
     - **Latency (p(95))**: 48.87ms (well below the 500ms threshold target).
     - **Checks Succeeded**: 100.00% (2,255 out of 2,255).

---

## 8. System Interface Screenshots

Below are screenshots illustrating the various tools and interfaces configured for the Event Ledger System:

### 8.1. HTTP REST Client Testing
Shows API requests being sent to the Gateway Service with JWT bearer tokens:
![HTTP REST Client](./HTTP-REST-client.png)

### 8.2. OpenAPI Swagger Documentation
Displays the interactive API schemas and endpoints for service integration:
![OpenAPI Swagger Docs](./OpenAPI-docs.png)

### 8.3. Grafana Telemetry Dashboard
The correlated Grafana dashboard visualizing scraped metrics and Loki logs linked to Tempo tracing spans:
![Grafana Dashboard](./Grafana_dashboard.png)

### 8.4. Email Alerts (Mailpit Catcher)
Displays the incoming Grafana-generated alerts caught by the local SMTP mail server interface:
![Mailpit Email Alerts](./email_alerts.png)
