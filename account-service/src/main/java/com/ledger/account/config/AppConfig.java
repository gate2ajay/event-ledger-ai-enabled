package com.ledger.account.config;

import com.ledger.common.aop.AuditedTransactionAspect;
import com.ledger.common.aop.TrackExecutionTimeAspect;
import com.ledger.common.tracing.TraceIdResponseFilter;
import com.ledger.account.repository.AccountTransactionRepository;
import com.ledger.account.security.M2mAuthenticationFilter;
import com.ledger.account.service.AccountService;
import com.ledger.account.service.AccountServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // 1. Security Beans
    @Bean
    public M2mAuthenticationFilter m2mAuthenticationFilter(
            @Value("${services.account.m2m-secret:internal-gateway-m2m-secret}") String m2mSecret) {
        return new M2mAuthenticationFilter(m2mSecret);
    }

    // 2. Service Beans
    @Bean
    public AccountService accountService(AccountTransactionRepository repository) {
        return new AccountServiceImpl(repository);
    }

    // 3. Observability / Telemetry Beans
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
