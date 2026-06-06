# Architectural Review: Event Ledger System

This document presents a professional architectural review of the Event Ledger microservices (`gateway-service` and `account-service`). It details critical design caveats, concurrency race conditions, data integrity pitfalls, and memory bottlenecks, providing senior architect-level recommendations and rationales.

---

## 🚨 1. Concurrency & Race Conditions

### 1.1. Gateway Service Concurrency Pitfall (HTTP 500 instead of 209 Conflict)
* **Location**: [EventServiceImpl.java](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/gateway-service/src/main/java/com/ledger/gateway/service/EventServiceImpl.java#L44-L61)
* **The Pitfall**:
  When two identical requests with the same `eventId` are submitted concurrently (at the exact same millisecond), both threads execute `repository.findById(eventId)` simultaneously. Since neither thread finds an existing event, both proceed to execute `repository.save(event)`.
  * **Database Behavior**: Since `eventId` is the primary key (`@Id`), the database unique constraint forces one of the inserts to fail with a primary key violation (`DataIntegrityViolationException`).
  * **Incorrect Handler Mapping**: The service try-catch block does not catch or handle `DataIntegrityViolationException`. It propagates up to the controller as a generic `Exception`, resulting in an **HTTP 500 Internal Server Error** instead of returning the expected **HTTP 209 Conflict** response.
* **Better Approach**:
  Catch `DataIntegrityViolationException` specifically in the service layer or map it to `DuplicateEventException` in the global exception handler:
  ```java
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
      // Map database constraint failures directly to HTTP 209 Conflict
      return ResponseEntity.status(209).build();
  }
  ```

---

## 📉 2. Performance & Memory Bottlenecks

### 2.1. JVM Memory Starvation (OOM) during Dynamic Balance Calculations
* **Location**: [AccountServiceImpl.java](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/account-service/src/main/java/com/ledger/account/service/AccountServiceImpl.java#L60)
* **The Pitfall**:
  The method `getBalance(String accountId)` loads all historical transactions for a given account into a JVM heap list (`List<AccountTransaction>`) and iterates through them to compute the sum:
  ```java
  List<AccountTransaction> transactions = repository.findByAccountId(accountId);
  ```
  If a single account has hundreds of thousands or millions of transactions (e.g., enterprise mainframe batch feeds), this operation will cause:
  1. **Garbage Collection (GC) pressure & Out Of Memory (OOM) errors** due to excessive object allocation in the JVM heap.
  2. **High Latency** due to serializing massive datasets from the database to the application server.
* **Better Approach**:
  Offload the summation computation to the database using an aggregate JPA query. This executes in $O(1)$ memory scope on the application tier:
  ```java
  @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0) " +
         "FROM AccountTransaction t WHERE t.accountId = :accountId")
  BigDecimal calculateBalance(@Param("accountId") String accountId);
  ```

---

## 🔐 3. Data & Distributed Transaction Integrity

### 3.1. Distributed Dual-Write Split-Brain Scenario
* **Location**: [EventServiceImpl.java](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/gateway-service/src/main/java/com/ledger/gateway/service/EventServiceImpl.java#L63-L85)
* **The Pitfall**:
  `processEvent` is marked with `@Transactional`. The service first persists the event state as `PENDING`, calls the external `accountClient.sendTransaction(...)` over HTTP, and then updates the local state to `COMPLETED`.
  If the HTTP call succeeds but the local gateway database transaction fails to commit (due to database lock timeouts, connection issues, or constraint failures during the final save), the gateway transaction **rolls back** (returning FAILED or no state in the gateway DB).
  However, the `account-service` has already committed the transaction on its end. This results in **split-brain data inconsistency**: the event is declared failed or lost at the gateway level, but money has been credited/debited in the account service.
* **Better Approach**:
  Implement the **Transactional Outbox Pattern** or a **saga pattern**:
  1. Persist the event to a local database outbox table in the same local transaction.
  2. Let a background worker pull outbox events and reliably publish them to a message broker (e.g., Kafka, RabbitMQ) with at-least-once delivery semantics.
  3. The `account-service` consumes events from the broker asynchronously and enforces idempotency.

---

## 💱 4. Financial Calculations & Business Logic Edge Cases

### 4.1. Multi-Currency Contamination Bug
* **Location**: [AccountServiceImpl.java](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/account-service/src/main/java/com/ledger/account/service/AccountServiceImpl.java#L66-L75)
* **The Pitfall**:
  The `getBalance` method accumulates transaction amounts without verifying if the currencies are identical:
  ```java
  balance = balance.add(tx.getAmount());
  ```
  If an account receives USD 100 and then EUR 50, the service sums them directly and returns `150` with whichever currency was processed last in the list loop. This introduces severe financial ledger inaccuracies.
* **Better Approach**:
  Enforce strict currency consistency:
  1. Bind accounts to a single designated currency (e.g., storing a `currency` field on an `Account` entity).
  2. Throw an exception at the gateway or account level if a transaction's currency does not match the account's base currency.

---

## 🧩 5. Architectural Cleanliness & Extensibility

### 5.1. Aspect Reflection Overhead
* **Location**: [AuditedTransactionAspect.java](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/common/src/main/java/com/ledger/common/aop/AuditedTransactionAspect.java)
* **The Pitfall**:
  The aspect inspects execution parameters using string-matching and raw reflection to extract transactional metadata. This is fragile (vulnerable to DTO renaming) and introduces CPU/reflection overhead.
* **Better Approach**:
  Use **Spring Expression Language (SpEL)** in the annotation to dynamically reference parameters cleanly:
  ```java
  @AuditedTransaction(action = "GATEWAY_PROCESS_EVENT", key = "#eventPayload.eventId")
  ```
  And evaluate the SpEL expression inside the aspect using standard Spring `ExpressionParser`.
