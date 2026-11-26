package com.groomdesk.billing;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Checks how a bill is paid. A bill is either paid in one mode (CASH, UPI or CARD, no parts) or split
 * (MIXED, two or three parts in different modes, each above zero, adding up exactly to the bill total).
 */
public final class PaymentRules {

    private PaymentRules() {
    }

    public record Part(PaymentMode mode, BigDecimal amount) {
    }

    /** Thrown with a message the cashier can act on. */
    public static class InvalidPaymentException extends IllegalArgumentException {
        InvalidPaymentException(String message) {
            super(message);
        }
    }

    /** Returns the parts to store: empty for a single-mode bill, the checked split for MIXED. */
    public static List<Part> check(BigDecimal total, PaymentMode mode, List<Part> parts) {
        List<Part> given = parts == null ? List.of() : parts;
        if (mode != PaymentMode.MIXED) {
            if (!given.isEmpty()) {
                throw new InvalidPaymentException("Split amounts need payment mode MIXED");
            }
            return List.of();
        }
        if (given.size() < 2 || given.size() > 3) {
            throw new InvalidPaymentException("A split payment needs two or three parts");
        }
        Set<PaymentMode> seen = EnumSet.noneOf(PaymentMode.class);
        BigDecimal sum = BigDecimal.ZERO;
        for (Part p : given) {
            if (p.mode() == null || !p.mode().isTender()) {
                throw new InvalidPaymentException("Each part must be CASH, UPI or CARD");
            }
            if (!seen.add(p.mode())) {
                throw new InvalidPaymentException(p.mode() + " appears twice in the split");
            }
            if (p.amount() == null || p.amount().signum() <= 0) {
                throw new InvalidPaymentException("Each part of a split must be more than zero");
            }
            if (p.amount().stripTrailingZeros().scale() > 2) {
                throw new InvalidPaymentException("Amounts can have at most 2 decimal places");
            }
            sum = sum.add(p.amount());
        }
        if (sum.compareTo(total) != 0) {
            throw new InvalidPaymentException("Split adds up to " + sum.setScale(2) + " but the bill total is "
                    + total.setScale(2));
        }
        return given.stream().map(p -> new Part(p.mode(), p.amount().setScale(2))).toList();
    }
}
