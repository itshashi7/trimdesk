package com.groomdesk.billing;

/** How a bill was paid. MIXED means split across modes; the split itself is in bill_payment. */
public enum PaymentMode {
    CASH,
    UPI,
    CARD,
    MIXED;

    /** A mode a customer can actually hand over (everything except MIXED). */
    public boolean isTender() {
        return this != MIXED;
    }
}
