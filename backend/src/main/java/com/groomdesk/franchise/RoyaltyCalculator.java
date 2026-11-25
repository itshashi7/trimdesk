package com.groomdesk.franchise;

import com.groomdesk.billing.BillCalculator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Royalty per franchise per month = Σ taxable_amount of its outlets × royalty_percent / 100 (docs/SPEC.md §3).
 * Rounded once on the monthly sum, not per bill, so it matches a manual calculation.
 */
public final class RoyaltyCalculator {

    private RoyaltyCalculator() {
    }

    public static BigDecimal royaltyDue(BigDecimal monthlyTaxableTotal, BigDecimal royaltyPercent) {
        return BillCalculator.percentOf(monthlyTaxableTotal, royaltyPercent);
    }

    public static BigDecimal royaltyDue(List<BigDecimal> taxableAmounts, BigDecimal royaltyPercent) {
        return royaltyDue(taxableAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add), royaltyPercent);
    }
}
