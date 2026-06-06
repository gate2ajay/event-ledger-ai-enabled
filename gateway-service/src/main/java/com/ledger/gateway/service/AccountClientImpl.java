package com.ledger.gateway.service;

import com.ledger.common.dto.TransactionRequest;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

public class AccountClientImpl implements AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClientImpl.class);

    private final RestClient restClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RetryRegistry retryRegistry;

    public AccountClientImpl(RestClient.Builder restClientBuilder,
                             String baseUrl,
                             String m2mSecret,
                             CircuitBreakerRegistry circuitBreakerRegistry,
                             BulkheadRegistry bulkheadRegistry,
                             RetryRegistry retryRegistry) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + m2mSecret)
                .build();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.retryRegistry = retryRegistry;
    }

    @Override
    public void sendTransaction(String accountId, TransactionRequest transactionRequest) {
        log.info("Sending transaction to Account Service for account: {}, eventId: {}", accountId, transactionRequest.getEventId());

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("accountService");
        Retry retry = retryRegistry.retry("accountService");

        // Combine Bulkhead, Circuit Breaker, and Retry
        Runnable decoratedRunnable = Decorators.ofRunnable(() -> {
            restClient.post()
                    .uri("/accounts/{accountId}/transactions", accountId)
                    .body(transactionRequest)
                    .retrieve()
                    .toBodilessEntity();
        })
        .withBulkhead(bulkhead)
        .withCircuitBreaker(circuitBreaker)
        .withRetry(retry)
        .decorate();

        try {
            decoratedRunnable.run();
            log.info("Successfully sent transaction to Account Service: {}", transactionRequest.getEventId());
        } catch (Exception e) {
            log.error("Failed to execute sendTransaction with resiliency wrappers", e);
            throw e;
        }
    }
}
