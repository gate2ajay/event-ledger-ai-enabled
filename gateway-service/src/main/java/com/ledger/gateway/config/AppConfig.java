package com.ledger.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledger.common.aop.AuditedTransactionAspect;
import com.ledger.common.aop.TrackExecutionTimeAspect;
import com.ledger.common.tracing.TraceIdResponseFilter;
import com.ledger.gateway.repository.GatewayEventRepository;
import com.ledger.gateway.security.JwtAuthenticationFilter;
import com.ledger.gateway.security.JwtHelper;
import com.ledger.gateway.service.*;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    // 1. Common Utility Beans (using autoconfigured ObjectMapper instead)

    // 2. Security Beans
    @Bean
    public JwtHelper jwtHelper(
            @Value("${jwt.secret:event-ledger-secure-key-1234567890-at-least-32-chars-long}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        return new JwtHelper(secret, expirationMs);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtHelper jwtHelper) {
        return new JwtAuthenticationFilter(jwtHelper);
    }

    // 3. Client & Integration Beans
    @Bean
    public AccountClient accountClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.account.url:http://localhost:8081}") String baseUrl,
            @Value("${services.account.m2m-secret:internal-gateway-m2m-secret}") String m2mSecret,
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            RetryRegistry retryRegistry) {
        return new AccountClientImpl(
                restClientBuilder,
                baseUrl,
                m2mSecret,
                circuitBreakerRegistry,
                bulkheadRegistry,
                retryRegistry
        );
    }

    // 4. Service Beans
    @Bean
    public EventService eventService(
            GatewayEventRepository repository,
            AccountClient accountClient,
            ObjectMapper objectMapper) {
        return new EventServiceImpl(repository, accountClient, objectMapper);
    }

    // 5. Observability / Telemetry Beans
    @Bean
    public TraceIdResponseFilter traceIdResponseFilter(Tracer tracer) {
        return new TraceIdResponseFilter(tracer);
    }

    @Bean
    public TrackExecutionTimeAspect trackExecutionTimeAspect(MeterRegistry meterRegistry) {
        return new TrackExecutionTimeAspect(meterRegistry);
    }

    @Bean
    public AuditedTransactionAspect auditedTransactionAspect() {
        return new AuditedTransactionAspect();
    }
}
