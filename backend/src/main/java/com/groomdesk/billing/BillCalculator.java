package com.groomdesk.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Bill money rules from CLAUDE.md §3 (tax-inclusive prices). Pure functions, BigDecimal only, HALF_UP to 2 decimals.
 * Shared by the seed runner and bill creation so both produce identical numbers.
 */
public final class BillCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BillCalculator() {
    }

    public record Amounts(BigDecimal subtotal, BigDecimal discount, BigDecimal taxableAmount,
                          BigDecimal taxAmount, BigDecimal total) {
    }

    /** subtotal = Σ item prices. */
    public static BigDecimal subtotal(List<BigDecimal> itemPrices) {
        return money(itemPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Menu prices include tax. Applies a flat discount (capped at subtotal), then backs the tax out:
     * total = subtotal − discount (what the customer pays);
     * taxable = total × 100 / (100 + rate), rounded HALF_UP; tax = total − taxable.
     */
    public static Amounts compute(List<BigDecimal> itemPrices, BigDecimal discount, BigDecimal taxRatePercent) {
        BigDecimal subtotal = subtotal(itemPrices);
        BigDecimal appliedDiscount = discount == null ? money(BigDecimal.ZERO) : money(discount);
        if (appliedDiscount.signum() < 0) {
            throw new IllegalArgumentException("Discount must not be negative");
        }
        if (appliedDiscount.compareTo(subtotal) > 0) {
            appliedDiscount = subtotal;
        }
        BigDecimal total = subtotal.subtract(appliedDiscount);
        BigDecimal taxable = total.multiply(HUNDRED).divide(HUNDRED.add(taxRatePercent), 2, RoundingMode.HALF_UP);
        BigDecimal tax = total.subtract(taxable);
        return new Amounts(subtotal, appliedDiscount, taxable, tax, total);
    }

    /** Converts a percentage discount into a flat amount on the given subtotal. */
    public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return money(base.multiply(percent).divide(HUNDRED));
    }

    /** commission = item price (pre-discount) × barber commission % / 100. */
    public static BigDecimal commission(BigDecimal itemPrice, BigDecimal commissionPercent) {
        return percentOf(itemPrice, commissionPercent);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
