package com.groomdesk.franchise;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoyaltyCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void royaltyIsSumOfTaxableAmountsTimesPercent() {
        List<BigDecimal> monthTaxable = List.of(bd("10000.00"), bd("2500.50"), bd("499.50"));

        assertThat(RoyaltyCalculator.royaltyDue(monthTaxable, bd("8.00"))).isEqualByComparingTo("1040.00");
    }

    @Test
    void fractionalPercentRoundsHalfUp() {
        // 123456.78 x 7.5% = 9259.2585 -> 9259.26
        assertThat(RoyaltyCalculator.royaltyDue(bd("123456.78"), bd("7.50"))).isEqualByComparingTo("9259.26");
    }

    @Test
    void roundsOnceOnTheMonthlySumNotPerBill() {
        // Per bill: 0.10 x 15% = 0.015 -> 0.02, x3 = 0.06. On the sum: 0.30 x 15% = 0.045 -> 0.05.
        List<BigDecimal> bills = List.of(bd("0.10"), bd("0.10"), bd("0.10"));

        assertThat(RoyaltyCalculator.royaltyDue(bills, bd("15"))).isEqualByComparingTo("0.05");
    }

    @Test
    void noBillsMeansNoRoyalty() {
        assertThat(RoyaltyCalculator.royaltyDue(List.of(), bd("10.00"))).isEqualByComparingTo("0.00");
    }
}
