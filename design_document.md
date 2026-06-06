# Design Document: Event Ledger System

This document specifies the technical design, API contracts, database schemas, security flows, telemetry protocols, and resiliency parameters for the **Event Ledger** microservice system.

---

## 1. System Architecture Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Client as External Client / Upstream
    participant Gateway as Event Gateway Service
    participant DB_GW as Gateway Database (H2)
    participant Account as Account Service
    participant DB_ACC as Account Database (H2)

    Client->>Gateway: POST /events (with JWT bearer token)
    Note over Gateway: 1. Validate JWT Signature<br/>2. Validate Event DTO Fields
    Gateway->>DB_GW: Check duplicate eventId
    alt Event ID exists (Idempotency Hit)
        DB_GW-->>Gateway: Return existing event record
        Gateway-->>Client: HTTP 209 Conflict (or HTTP 200 with original event)
    else Event ID is unique
        Gateway->>DB_GW: Persist event (Status: PENDING)
        
        Note over Gateway: Apply Resiliency Wrapper:<br/>[Bulkhead] -> [Circuit Breaker] -> [Timeout/Retry]
        
        Gateway->>Account: POST /accounts/{accountId}/transactions (with M2M Secret Token & traceparent header)
        
        Note over Account: 1. Validate M2M Token<br/>2. Check duplicate eventId
        Account->>DB_ACC: Persist transaction & recalculate balance
        DB_ACC-->>Account: Transaction saved
        Account-->>Gateway: HTTP 201 Created (Success)
        
        Gateway->>DB_GW: Update event (Status: COMPLETED)
        Gateway-->>Client: HTTP 201 Created (Original Event)
    end
```

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

### 5.4. Bulkhead
* **Type**: Semaphore or Threadpool (ThreadPool bulkhead is preferred for RestClient isolation).
* **Max Concurrent Calls**: `10`.
* **Max Queue Capacity**: `5`. If more than 15 requests are concurrently handled, additional requests fail fast with `BulkheadFullException`.
