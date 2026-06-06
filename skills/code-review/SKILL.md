---
name: java-code-review
description: Guidance on performing Java code reviews enforcing DRY, KISS, SOLID principles, and appropriate design patterns (like Thread Pool Bulkhead).
---

# Java Code Review Skill

This skill provides guidelines and patterns to perform high-quality Java code reviews. It focuses on ensuring code simplicity, maintainability, scalability, and resilience, aligned with Java and Spring Boot best practices.

---

## 🧩 Core Principles

### 1. DRY (Don't Repeat Yourself)
* **Rule**: Every piece of knowledge or logic must have a single, unambiguous representation within a system.
* **Review Checklist**:
  * Check for duplicate code blocks across controllers, services, or repository layers.
  * Extract common data structures (DTOs), utility helpers, and cross-cutting concerns (e.g., AOP aspects, filter configurations) into shared modules like `common`.
  * Leverage Spring's inheritance or composition patterns for database entities or API responses.

### 2. KISS (Keep It Simple, Stupid)
* **Rule**: Design systems and write code that is simple and easy to understand. Avoid unnecessary layers, abstractions, or premature optimizations.
* **Review Checklist**:
  * Are method lengths under 30-50 lines? Split complex functions into smaller, descriptive private helper methods.
  * Avoid deep nested loops or heavily nested conditional statements (`if-else`). Prefer guard clauses and early exits.
  * Ensure variables and methods have clear, self-documenting names rather than obscure abbreviations.
  * Use standard Java stream operations and built-in collectors rather than complex custom iteration loops.

### 3. SOLID Principles
* **Single Responsibility Principle (SRP)**: Each class should have only one reason to change (e.g., separate controllers for HTTP validation from business services, and separate services from persistence layers).
* **Open/Closed Principle (OCP)**: Code should be open for extension but closed for modification. Use interfaces and polymorphism to add new behaviors instead of modifying existing conditional chains.
* **Liskov Substitution Principle (LSP)**: Derived classes must be completely substitutable for their base classes without breaking correctness.
* **Interface Segregation Principle (ISP)**: Create small, specific interfaces rather than large, monolithic ones (e.g., separate read-only query interfaces from write interfaces if needed).
* **Dependency Inversion Principle (DIP)**: Depend on abstractions (interfaces) rather than concrete implementations (e.g., inject constructor-based dependencies using Spring interfaces like `AccountClient` rather than `AccountClientImpl`).

---

## 🛠️ Resiliency and Concurrency Patterns

### 1. Bulkhead Pattern (Thread Pool vs Semaphore)
To prevent cascading failures and resource exhaustion when calling external or slow systems, isolate execution resources.

* **Semaphore Bulkhead**:
  * Limits the number of concurrent executions using a simple counter.
  * Executes calls on the *caller's thread*.
  * Best for CPU-bound tasks or when the caller's thread pool is large enough.
* **Thread Pool Bulkhead** (Preferred for REST/M2M integration):
  * Assigns a dedicated thread pool and queue for downstream calls.
  * Executes calls on *isolated threads*, freeing up the main web container threads (e.g., Tomcat threads).
  * Best for I/O-bound integrations or calling remote APIs (like Gateway to Account Service calls).

#### Spring Boot / Resilience4j Thread Pool Bulkhead Implementation Pattern:
When reviewing bulkhead configuration, ensure it follows the structure below:
```yaml
resilience4j.threadpoolbulkhead:
  instances:
    accountServiceBulkhead:
      maxThreadPoolSize: 10
      coreThreadPoolSize: 5
      queueCapacity: 5
      keepAliveDuration: 20ms
```
Review the Java integration to ensure the bulkhead is properly decorated:
```java
// Check that Bulkhead is used in combination with Timeouts and Circuit Breakers
ThreadPoolBulkhead bulkhead = ThreadPoolBulkhead.of("accountServiceBulkhead", config);
Supplier<CompletionStage<Response>> entry = ThreadPoolBulkhead.decorateRunnable(bulkhead, () -> service.call());
```

---

## 📝 Code Review Checklist for Java Services

Use this checklist during every code review iteration:

| Area | Focus Item | Pass/Fail Criteria |
| :--- | :--- | :--- |
| **Simplicity** | Guard Clauses | Are validation/null checks placed at the top of methods with early returns/exceptions? |
| **Resilience** | Exception Translation | Do controllers catch system exceptions and convert them to standard REST RFC 7807 `ProblemDetail` responses? |
| **Security** | Auth Propagation | Are M2M endpoints checking credentials (e.g., `Authorization: Bearer <secret>`) and ignoring public JWTs where internal? |
| **Performance** | Database Queries | Are database queries avoiding N+1 problems? (Ensure proper JPA entity graphs or joins are used). |
| **Clean Code** | Magic Numbers | Are all timeout limits, status code integers, or special constants defined as static final variables or externalized config? |
| **Observability** | MDC / Tracing | Are asynchronous/multithreaded operations correctly propagating the trace context (`traceId` and `spanId`) to new threads? |
