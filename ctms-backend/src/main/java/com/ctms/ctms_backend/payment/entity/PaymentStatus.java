package com.ctms.ctms_backend.payment.entity;

/** Linear PENDING -> ON_HOLD -> RELEASED, no re-hold after release (BRD Story 04 doesn't describe
 * that case). PENDING payments are real, rule-generated commitments, not "blocked" -- only
 * ON_HOLD is excluded from a budget's "actual" total. */
public enum PaymentStatus {
    PENDING,
    ON_HOLD,
    RELEASED
}
