---
name: security-guardrails
description: Comprehensive security guidelines and guardrails for AI agents regarding application, database, communication, API, and resource security, with mandatory Human-in-the-Loop (HITL) triggers.
---

# Security & Integrity Guardrails for AI Agents

This document defines the mandatory security rules, design patterns, and boundaries that AI agents must follow when modifying, developing, or configuring the Event Ledger system. It explicitly details when agents must halt execution and request Human-in-the-Loop (HITL) approval.

---

## 🚨 Mandatory Human-in-the-Loop (HITL) Triggers

Agents are forbidden from making autonomous decisions that alter the security posture or data integrity of the system. **You must stop and ask the user for confirmation if your tasks involve:**

1. **Authentication & Authorization Changes**:
   * Modifying JWT validation, token generation, or issuer endpoints.
   * Altering Machine-to-Machine (M2M) shared secrets or the validation filters.
   * Relaxing security rules in gateway filters, Spring Security configs, or cross-origin (CORS) settings.
2. **Database Integrity & Ledger Alterations**:
   * Writing scripts that modify existing transactions, historical records, or audit logs directly in the database.
   * Removing unique constraint rules or altering double-sided idempotency controls.
3. **Log & Audit Deletion**:
   * Clearing, disabling, or modifying AOP aspects related to transaction auditing (`AuditedTransactionAspect`) or execution tracking.
   * Disabling metrics scraping, Promtail configurations, or Loki log routing.
4. **Security Tooling & Libraries**:
   * Upgrading or replacing critical framework libraries (e.g., Spring Boot Starter Security, Resilience4j) without a predefined and approved plan.

---

## 🔒 Security Best Practices by Domain

### 1. Application & Code Security
* **Input Validation**: Always enforce strict annotation-based validation (e.g., `@NotNull`, `@Min`, `@Size`, `@Pattern`) on all incoming request objects (DTOs) at the controller level.
* **Error Handling Isolation**: Never return raw database stack traces or system logs to the client. Always wrap errors using standard Spring Boot 3 `ProblemDetail` responses.
* **Safe Dependencies**: Do not introduce unverified third-party libraries. If dependencies are needed, review vulnerabilities first.

### 2. Database Security
* **SQL Injection Prevention**: Always use Spring Data JPA repository interfaces or parameterized query strings (`NamedParameterJdbcTemplate`). Never concatenate raw strings to construct SQL queries.
* **Console Safety**: Ensure H2 console access (`/h2-console`) is restricted to local development environments only and disabled or strictly firewalled in production deployment descriptors.

### 3. Communication Security
* **Transport Encryption**: Enforce TLS/HTTPS across all external communication routes.
* **Token Handshakes**: Verify that gateway-to-service communication is secured via designated request header handshakes (e.g., `Authorization: Bearer <m2m-secret>`).
* **Trace Propagation Context**: Ensure distributed trace headers (`traceparent`) are validated and passed downstream to prevent tracing context splits.

### 4. API & Resource Protection
* **Resource Exhaustion Isolation**: Always decorate remote integration calls with Resilience4j bulkhead (thread pool/semaphore) and circuit breaker configurations to prevent thread pool starvation or cascade outages.
* **Rate Limiting**: Apply rate-limiting filters on external gateways to restrict request bursts and block potential Denial of Service (DoS) attacks.
