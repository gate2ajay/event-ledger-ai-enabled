package com.ledger.gateway.service;

import com.ledger.common.dto.TransactionRequest;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AccountClientImplTest {

    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private RetryRegistry retryRegistry;

    private AccountClientImpl accountClient;

    @BeforeEach
    public void setUp() {
        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        // Use real registries to avoid deep mocking Resilience4j internals
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();

        accountClient = new AccountClientImpl(
                restClientBuilder,
                "http://localhost:8081",
                "secret-key",
                circuitBreakerRegistry,
                bulkheadRegistry,
                retryRegistry
        );
    }

    @Test
    public void testSendTransaction_Success() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/accounts/{accountId}/transactions"), eq("acct-123"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(eq(request))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        accountClient.sendTransaction("acct-123", request);

        verify(restClient, times(1)).post();
        verify(requestBodyUriSpec, times(1)).uri(eq("/accounts/{accountId}/transactions"), eq("acct-123"));
        verify(requestBodySpec, times(1)).body(eq(request));
        verify(responseSpec, times(1)).toBodilessEntity();
    }

    @Test
    public void testSendTransaction_Failure() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/accounts/{accountId}/transactions"), eq("acct-123"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(eq(request))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        
        RuntimeException mockException = new RuntimeException("Http Connection Error");
        when(responseSpec.toBodilessEntity()).thenThrow(mockException);

        assertThatThrownBy(() -> accountClient.sendTransaction("acct-123", request))
                .isSameAs(mockException);

        // By default, the Resilience4J Retry registry will attempt 3 times
        verify(restClient, times(3)).post();
    }
}
