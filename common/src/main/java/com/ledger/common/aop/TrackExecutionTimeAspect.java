package com.ledger.common.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@Aspect
public class TrackExecutionTimeAspect {

    private static final Logger log = LoggerFactory.getLogger(TrackExecutionTimeAspect.class);
    private final MeterRegistry meterRegistry;

    public TrackExecutionTimeAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(trackExecutionTime)")
    public Object profile(ProceedingJoinPoint pjp, TrackExecutionTime trackExecutionTime) throws Throwable {
        long start = System.nanoTime();
        String name = trackExecutionTime.value().isEmpty() ? pjp.getSignature().getName() : trackExecutionTime.value();
        
        try {
            Object output = pjp.proceed();
            long duration = System.nanoTime() - start;
            long millis = TimeUnit.NANOSECONDS.toMillis(duration);
            
            log.info("{\"event\":\"execution_time\",\"method\":\"{}\",\"duration_ms\":{}}", name, millis);
            
            if (meterRegistry != null) {
                Timer.builder("method.execution.time")
                        .tag("method", name)
                        .register(meterRegistry)
                        .record(duration, TimeUnit.NANOSECONDS);
            }
            
            return output;
        } catch (Throwable t) {
            long duration = System.nanoTime() - start;
            long millis = TimeUnit.NANOSECONDS.toMillis(duration);
            log.warn("{\"event\":\"execution_time_failure\",\"method\":\"{}\",\"duration_ms\":{},\"exception\":\"{}\"}", 
                    name, millis, t.getClass().getSimpleName());
            throw t;
        }
    }
}
