package com.ctms.ctms_backend.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.payment.rules.PaymentRuleOutcome;
import com.ctms.ctms_backend.rules.RuleSetService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRuleServiceTest {

    @Mock private RuleSetService ruleSetService;

    @InjectMocks
    private PaymentRuleService paymentRuleService;

    @Test
    void evaluate_visitCompleted_returnsOutcome() {
        PaymentRuleOutcome outcome = new PaymentRuleOutcome("MONITORING", new BigDecimal("500.00"), new BigDecimal("1.0"), null, "USD");
        when(ruleSetService.evaluate(eq("PAYMENT_RULES_DEFAULT"), anyList())).thenReturn(List.of(outcome));

        Optional<PaymentRuleOutcome> result = paymentRuleService.evaluate("VISIT_COMPLETED");

        assertTrue(result.isPresent());
        assertEquals("MONITORING", result.get().getCostCategory());
        assertEquals(new BigDecimal("500.00"), result.get().getBaseAmount());
    }

    @Test
    void evaluate_siteActivated_returnsOutcome() {
        PaymentRuleOutcome outcome = new PaymentRuleOutcome("SITE_PAYMENTS", new BigDecimal("2000.00"), new BigDecimal("1.0"), null, "USD");
        when(ruleSetService.evaluate(eq("PAYMENT_RULES_DEFAULT"), anyList())).thenReturn(List.of(outcome));

        Optional<PaymentRuleOutcome> result = paymentRuleService.evaluate("SITE_ACTIVATED");

        assertTrue(result.isPresent());
        assertEquals("SITE_PAYMENTS", result.get().getCostCategory());
    }

    @Test
    void evaluate_noMatchingRule_returnsEmpty() {
        when(ruleSetService.evaluate(eq("PAYMENT_RULES_DEFAULT"), anyList())).thenReturn(List.of());

        Optional<PaymentRuleOutcome> result = paymentRuleService.evaluate("MILESTONE_REACHED_LPO");

        assertTrue(result.isEmpty());
    }
}
