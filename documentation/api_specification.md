# Event Ledger API Specification

This document provides detailed API documentation for all services exposed by the Event Ledger project, including Gateway Service endpoints and Account Service endpoints.

---

## Service Overview

The project is structured with two main functional API services:
1. **Gateway Service (Host Port `8080`)**: Entry point for client requests, handles authentication, and acts as the edge route manager.
2. **Account Service (Host Port `8081`)**: Internal service managing bank/event ledger account state and balances (typically reached via Gateway but can also be queried directly if allowed).

---

## Interactive Swagger UI / OpenAPI Docs

Both microservices are equipped with `springdoc-openapi` to automatically generate and host interactive Swagger UIs and OpenAPI specifications.

### Gateway Service (`:8080`)
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Account Service (`:8081`)
- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **OpenAPI JSON Spec**: [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

---

## Authentication

All endpoints under the Gateway Service (except `/auth/token`) require a JSON Web Token (JWT) Bearer Token header:

```http
Authorization: Bearer <your_jwt_token>
```

### Get JWT Token

Obtains a temporary JWT token for testing.

- **URL**: `/auth/token`
- **Method**: `GET`
- **Authentication**: None
- **Query Parameters**:
  - `client` (String, optional, default: `test-user`): The client name/ID to generate a token for.
- **Success Response (200 OK)**:
  - **Content-Type**: `application/json`
  - **Body**:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiJ9.eyNzdWIiOiJrNi10ZXN0ZXIiLCJpYXQiOjE3MTc2NTExNzcsImV4cCI6MTcxNzY1NDc3N30.xxx...",
      "token_type": "Bearer"
    }
    ```

---

## Gateway Service APIs (`http://localhost:8080`)

### 1. Ingest Event

Submits a financial ledger event to be processed and posted.

- **URL**: `/events`
- **Method**: `POST`
- **Authentication**: Required (Bearer Token)
- **Headers**:
  - `Content-Type: application/json`
- **Request Body (EventPayload)**:
  - `eventId` (String, Required): A unique identifier for the event.
  - `accountId` (String, Required): ID of the account affected by the event.
  - `type` (String, Required): Must be either `"CREDIT"` or `"DEBIT"`.
  - `amount` (Decimal, Required): Positive transaction amount (must be greater than 0).
  - `currency` (String, Required): 3-character ISO currency code (e.g. `"USD"`).
  - `eventTimestamp` (ISO-8601 Timestamp, Required): Time when event was created.
  - `metadata` (Map, Optional): Key-value pairs for trace context, customer tags, etc.
- **Example Request**:
  ```json
  {
    "eventId": "evt-1234567890",
    "accountId": "acc-savings-001",
    "type": "CREDIT",
    "amount": 250.50,
    "currency": "USD",
    "eventTimestamp": "2026-06-05T22:45:00Z",
    "metadata": {
      "channel": "mobile-app",
      "device": "iOS-17.4"
    }
  }
  ```
- **Responses**:
  - **201 Created**: Event successfully validated, persisted, and forwarded. Returns the ingested payload.
  - **209 Conflict (Custom)**: Handled gracefully if an event with the same `eventId` has already been processed (Idempotent handling).
  - **400 Bad Request**: Invalid body parameters (e.g. negative amount, invalid currency format).
  - **401 Unauthorized**: Missing or invalid Bearer Token.

---

### 2. Retrieve Event by ID

Fetch a specific event by its ID.

- **URL**: `/events/{id}`
- **Method**: `GET`
- **Authentication**: Required (Bearer Token)
- **Path Parameters**:
  - `id` (String): The unique event ID.
- **Success Response (200 OK)**:
  - **Content-Type**: `application/json`
  - **Body**: Returns the corresponding `EventPayload` model.
- **Responses**:
  - **404 Not Found**: Event with the given ID does not exist.
  - **401 Unauthorized**: Missing or invalid Bearer Token.

---

### 3. Retrieve Events by Account ID

Fetch a list of all events associated with a specific account.

- **URL**: `/events`
- **Method**: `GET`
- **Authentication**: Required (Bearer Token)
- **Query Parameters**:
  - `account` (String, Required): The target Account ID.
- **Success Response (200 OK)**:
  - **Content-Type**: `application/json`
  - **Body**: A JSON list of `EventPayload` objects.
    ```json
    [
      {
        "eventId": "evt-1234567890",
        "accountId": "acc-savings-001",
        "type": "CREDIT",
        "amount": 250.50,
        "currency": "USD",
        "eventTimestamp": "2026-06-05T22:45:00Z",
        "metadata": null
      }
    ]
    ```

---

## Account Service APIs (`http://localhost:8081`)

These endpoints represent the ledger state. Under production workloads, these are internal backend APIs, but they are exposed on port `8081` in development.

### 1. Apply Transaction

Instructs the account database to post a new transaction to the account balance.

- **URL**: `/accounts/{accountId}/transactions`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/json`
- **Request Body (TransactionRequest)**:
  - `eventId` (String, Required): The tracking event ID.
  - `type` (String, Required): `"CREDIT"` or `"DEBIT"`.
  - `amount` (Decimal, Required): Transaction amount (> 0).
  - `currency` (String, Required): 3-character ISO code.
  - `eventTimestamp` (ISO-8601 Timestamp, Required): Timestamp.
- **Responses**:
  - **201 Created**: Transaction posted successfully. No response body.
  - **400 Bad Request**: Invalid request data or validation failure.
  - **409 Conflict**: Transaction with this `eventId` already applied (Idempotent database guard).

---

### 2. Get Account Balance

Retrieve current balance and last updated metadata for an account.

- **URL**: `/accounts/{accountId}/balance`
- **Method**: `GET`
- **Success Response (200 OK)**:
  - **Content-Type**: `application/json`
  - **Body (AccountBalanceResponse)**:
    ```json
    {
      "accountId": "acc-savings-001",
      "balance": 1500.25,
      "currency": "USD",
      "lastUpdated": "2026-06-05T22:47:10Z"
    }
    ```

---

### 3. Get Account Details

Retrieve the account balance details along with a list of recent transactions.

- **URL**: `/accounts/{accountId}`
- **Method**: `GET`
- **Success Response (200 OK)**:
  - **Content-Type**: `application/json`
  - **Body (AccountDetailsResponse)**:
    ```json
    {
      "accountId": "acc-savings-001",
      "balance": 1500.25,
      "currency": "USD",
      "lastUpdated": "2026-06-05T22:47:10Z",
      "transactions": [
        {
          "eventId": "evt-1234567890",
          "type": "CREDIT",
          "amount": 250.50,
          "currency": "USD",
          "eventTimestamp": "2026-06-05T22:45:00Z"
        }
      ]
    }
    ```
