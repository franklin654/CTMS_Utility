package com.ctms.ctms_backend.payment.rules;

/** Drools fact -- the trigger event that a Payment is being auto-generated for. */
public class PaymentTriggerFact {

    private final String eventCode;

    public PaymentTriggerFact(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventCode() {
        return eventCode;
    }
}
