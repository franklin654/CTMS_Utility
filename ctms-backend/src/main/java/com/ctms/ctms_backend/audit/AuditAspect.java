package com.ctms.ctms_backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();

    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object logAuditedCall(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Expression expression = PARSER.parseExpression(audited.entityId());
            var context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), PARAM_NAMES);
            Object entityId = expression.getValue(context);
            String afterValue = objectMapper.writeValueAsString(joinPoint.getArgs());
            auditService.record(
                    audited.entityName(), String.valueOf(entityId), audited.action(), afterValue);
        } catch (Exception e) {
            // Auditing must never break the underlying business operation, which already succeeded.
            log.error("Failed to write audit log for {}", joinPoint.getSignature(), e);
        }
        return result;
    }
}
