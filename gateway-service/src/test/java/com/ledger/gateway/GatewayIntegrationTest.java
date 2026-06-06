package com.ledger.gateway;

import com.ledger.common.dto.EventPayload;
import com.ledger.gateway.repository.GatewayEventRepository;
import com.ledger.gateway.security.JwtHelper;
import com.ledger.gateway.service.AccountClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private GatewayEventRepository eventRepository;

    @MockBean
    private AccountClient accountClient;

    private String validToken;

    @BeforeEach
    public void setUp() {
        RestAssured.port = port;
        eventRepository.deleteAll();
        validToken = jwtHelper.generateToken("test-client");
    }

    @Test
    public void testCreateEvent_Unauthorized() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-100")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(401);
    }

    @Test
    public void testCreateEvent_Success() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-100")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(201)
                .body("eventId", equalTo("evt-100"))
                .body("accountId", equalTo("acct-100"))
                .body("amount", equalTo(50.0f))
                .body("type", equalTo("CREDIT"));
    }

    @Test
    public void testCreateEvent_IdempotencyHit() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-100")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        // 1st request -> Created
        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(201);

        // 2nd request -> Duplicate (209 Conflict with original body)
        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(209)
                .body("eventId", equalTo("evt-100"));
    }

    @Test
    public void testCreateEvent_ValidationFailure() {
        EventPayload payload = EventPayload.builder()
                .eventId("") // Empty eventId should fail validation
                .accountId("acct-100")
                .type("INVALID_TYPE") // Invalid CREDIT/DEBIT
                .amount(new BigDecimal("-10.00")) // Negative amount
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .log().all()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("detail", equalTo("Input validation failed"))
                .body("errors.eventId", notNullValue())
                .body("errors.type", notNullValue())
                .body("errors.amount", notNullValue())
                .body("trace_id", notNullValue());
    }

    @Test
    public void testCreateEvent_CircuitBreakerOpenException() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-300")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        // Mock Account Client to throw a CallNotPermittedException (simulate Circuit Breaker is open)
        CallNotPermittedException cbException = Mockito.mock(CallNotPermittedException.class);
        when(cbException.getMessage()).thenReturn("Circuit breaker is open");
        doThrow(cbException).when(accountClient).sendTransaction(anyString(), any());

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .log().all()
                .statusCode(503)
                .body("detail", equalTo("Account Service is currently unavailable (Circuit Breaker open)"))
                .body("trace_id", notNullValue());
    }

    @Test
    public void testCreateEvent_BulkheadFullException() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-400")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        io.github.resilience4j.bulkhead.Bulkhead bh = io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("accountServiceBulkhead");
        BulkheadFullException bfException = BulkheadFullException.createBulkheadFullException(bh);
        doThrow(bfException).when(accountClient).sendTransaction(anyString(), any());

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(429)
                .body("detail", equalTo("System is overloaded. Please try again later."));
    }

    @Test
    public void testCreateEvent_TimeoutException() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-500")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        org.mockito.stubbing.Answer<Void> answer = invocation -> {
            throwSneaky(new java.util.concurrent.TimeoutException("Timeout"));
            return null;
        };
        org.mockito.Mockito.doAnswer(answer).when(accountClient).sendTransaction(anyString(), any());

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(504)
                .body("detail", equalTo("Account Service call timed out."));
    }

    @Test
    public void testCreateEvent_GenericException() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-600")
                .accountId("acct-100")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        doThrow(new RuntimeException("Uncaught error")).when(accountClient).sendTransaction(anyString(), any());

        given()
                .header("Authorization", "Bearer " + validToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/events")
                .then()
                .statusCode(500)
                .body("detail", equalTo("An unexpected internal error occurred. Please refer to trace ID for investigation."));
    }

    @Test
    public void testGetToken() {
        given()
                .queryParam("client", "custom-client")
                .when()
                .get("/auth/token")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("token_type", equalTo("Bearer"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwSneaky(Throwable t) throws T {
        throw (T) t;
    }
}
