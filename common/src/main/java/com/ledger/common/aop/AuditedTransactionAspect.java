package com.ledger.common.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@Aspect
public class AuditedTransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditedTransactionAspect.class);
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Before("@annotation(auditedTransaction)")
    public void audit(JoinPoint joinPoint, AuditedTransaction auditedTransaction) {
        String action = auditedTransaction.action();
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        
        String eventId = evaluateExpression(auditedTransaction.eventId(), signature, args);
        String accountId = evaluateExpression(auditedTransaction.accountId(), signature, args);
        String type = evaluateExpression(auditedTransaction.type(), signature, args);
        String amount = evaluateExpression(auditedTransaction.amount(), signature, args);

        log.info("{\"event\":\"audit_log\",\"action\":\"{}\",\"eventId\":\"{}\",\"accountId\":\"{}\",\"type\":\"{}\",\"amount\":\"{}\"}", 
                action, eventId, accountId, type, amount);
    }

    private String evaluateExpression(String expressionStr, MethodSignature signature, Object[] args) {
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            return "UNKNOWN";
        }
        try {
            java.lang.reflect.Method method = signature.getMethod();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            Object value = parser.parseExpression(expressionStr).getValue(context);
            return value != null ? value.toString() : "NULL";
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression: {}", expressionStr, e);
            return "UNKNOWN";
        }
    }
}
