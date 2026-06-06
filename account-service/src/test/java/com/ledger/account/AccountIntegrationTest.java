package com.ledger.account;

import com.ledger.common.dto.TransactionRequest;
import com.ledger.account.repository.AccountTransactionRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AccountIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${services.account.m2m-secret:internal-gateway-m2m-secret}")
    private String m2mSecret;

    @Autowired
    private AccountTransactionRepository repository;

    @BeforeEach
    public void setUp() {
        RestAssured.port = port;
        repository.deleteAll();
    }

    @Test
    public void testApplyTransaction_Unauthorized() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-1")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/accounts/acct-123/transactions")
                .then()
                .statusCode(401);
    }

    @Test
    public void testApplyTransaction_Success() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-1")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/accounts/acct-123/transactions")
                .then()
                .statusCode(201);
    }

    @Test
    public void testApplyTransaction_DuplicateEventId() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-1")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        // 1st time
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/accounts/acct-123/transactions")
                .then()
                .statusCode(201);

        // 2nd time -> 209 Conflict
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/accounts/acct-123/transactions")
                .then()
                .statusCode(209)
                .body("detail", equalTo("Duplicate transaction detected for event ID: evt-1"))
                .body("trace_id", notNullValue());
    }

    @Test
    public void testGetBalance_SuccessfulCalculation() {
        String accountId = "acct-999";
        
        // Setup initial transactions
        TransactionRequest t1 = TransactionRequest.builder().eventId("e1").type("CREDIT").amount(new BigDecimal("200.00")).currency("USD").eventTimestamp(Instant.now()).build();
        TransactionRequest t2 = TransactionRequest.builder().eventId("e2").type("DEBIT").amount(new BigDecimal("50.00")).currency("USD").eventTimestamp(Instant.now()).build();

        // Apply transactions
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(t1)
                .post("/accounts/" + accountId + "/transactions")
                .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(t2)
                .post("/accounts/" + accountId + "/transactions")
                .then()
                .statusCode(201);

        // Fetch balance
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .when()
                .get("/accounts/" + accountId + "/balance")
                .then()
                .statusCode(200)
                .body("accountId", equalTo(accountId))
                .body("balance", equalTo(150.0f))
                .body("currency", equalTo("USD"));

        // Fetch details
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .when()
                .get("/accounts/" + accountId)
                .then()
                .statusCode(200)
                .body("accountId", equalTo(accountId))
                .body("balance", equalTo(150.0f))
                .body("transactions.size()", equalTo(2));
    }

    @Test
    public void testApplyTransaction_CurrencyMismatch() {
        String accountId = "acct-curr-check";

        TransactionRequest t1 = TransactionRequest.builder()
                .eventId("evt-curr-1")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        TransactionRequest t2 = TransactionRequest.builder()
                .eventId("evt-curr-2")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("EUR") // Mismatched currency
                .eventTimestamp(Instant.now())
                .build();

        // 1st transaction (USD) -> Success
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(t1)
                .post("/accounts/" + accountId + "/transactions")
                .then()
                .statusCode(201);

        // 2nd transaction (EUR) -> 400 Bad Request
        given()
                .header("Authorization", "Bearer " + m2mSecret)
                .contentType(ContentType.JSON)
                .body(t2)
                .post("/accounts/" + accountId + "/transactions")
                .then()
                .statusCode(400)
                .body("detail", org.hamcrest.Matchers.containsString("Currency mismatch"));
    }
}
