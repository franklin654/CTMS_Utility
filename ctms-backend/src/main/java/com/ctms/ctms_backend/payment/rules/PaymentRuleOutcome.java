package com.ctms.ctms_backend.payment.rules;

import java.math.BigDecimal;

/** Drools fact -- what PAYMENT_RULES_DEFAULT decides for a given trigger event: cost category
 * plus the base amount / multiplier / cap components (final amount computed in Java by
 * PaymentService so the breakdown stays visible/auditable, not just an opaque final number). */
public class PaymentRuleOutcome {

    private final String costCategory;
    private final BigDecimal baseAmount;
    private final BigDecimal multiplier;
    private final BigDecimal capAmount;
    private final String currency;

    public PaymentRuleOutcome(String costCategory, BigDecimal baseAmount, BigDecimal multiplier, BigDecimal capAmount, String currency) {
        this.costCategory = costCategory;
        this.baseAmount = baseAmount;
        this.multiplier = multiplier;
        this.capAmount = capAmount;
        this.currency = currency;
    }

    public String getCostCategory() {
        return costCategory;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public BigDecimal getCapAmount() {
        return capAmount;
    }

    public String getCurrency() {
        return currency;
    }
}
