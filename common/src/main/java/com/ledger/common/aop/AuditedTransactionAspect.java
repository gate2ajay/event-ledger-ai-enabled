package com.ledger.common.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class AuditedTransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditedTransactionAspect.class);

    @Before("@annotation(auditedTransaction)")
    public void audit(JoinPoint joinPoint, AuditedTransaction auditedTransaction) {
        String action = auditedTransaction.action();
        Object[] args = joinPoint.getArgs();
        
        String eventId = "UNKNOWN";
        String accountId = "UNKNOWN";
        String type = "UNKNOWN";
        String amount = "UNKNOWN";

        // Attempt to inspect args to extract transaction properties
        for (Object arg : args) {
            if (arg == null) continue;
            
            try {
                // Check if it is EventPayload
                Class<?> clazz = arg.getClass();
                if (clazz.getSimpleName().equals("EventPayload")) {
                    eventId = getFieldValue(arg, "eventId");
                    accountId = getFieldValue(arg, "accountId");
                    type = getFieldValue(arg, "type");
                    amount = getFieldValue(arg, "amount");
                    break;
                } else if (clazz.getSimpleName().equals("TransactionRequest")) {
                    eventId = getFieldValue(arg, "eventId");
                    // Try to get accountId if available in method signature or route variables
                    type = getFieldValue(arg, "type");
                    amount = getFieldValue(arg, "amount");
                }
            } catch (Exception e) {
                // Ignore parsing errors for safety in aspect
            }
        }

        log.info("{\"event\":\"audit_log\",\"action\":\"{}\",\"eventId\":\"{}\",\"accountId\":\"{}\",\"type\":\"{}\",\"amount\":\"{}\"}", 
                action, eventId, accountId, type, amount);
    }

    private String getFieldValue(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(target);
            return val != null ? val.toString() : "NULL";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
