package com.groomdesk.billing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillCalculatorTest {

    private static final BigDecimal GST_18 = new BigDecimal("18.00");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static List<BigDecimal> prices(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Nested
    class BillTotal {

        @Test
        void subtotalIsSumOfItemPricesAndTaxIsOnTaxableAmount() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("250.00", "150.00"), null, GST_18);

            assertThat(a.subtotal()).isEqualByComparingTo("400.00");
            assertThat(a.discount()).isEqualByComparingTo("0.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("400.00");
            assertThat(a.taxAmount()).isEqualByComparingTo("72.00");
            assertThat(a.total()).isEqualByComparingTo("472.00");
        }

        @Test
        void flatDiscountReducesTaxableAmountBeforeTax() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("250.00", "150.00"), bd("100"), GST_18);

            assertThat(a.discount()).isEqualByComparingTo("100.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("300.00");
            assertThat(a.taxAmount()).isEqualByComparingTo("54.00");
            assertThat(a.total()).isEqualByComparingTo("354.00");
        }

        @Test
        void percentDiscountIsTakenOnSubtotal() {
            List<BigDecimal> items = prices("150.00", "150.00", "250.00");
            BigDecimal discount = BillCalculator.percentOf(BillCalculator.subtotal(items), bd("15"));

            BillCalculator.Amounts a = BillCalculator.compute(items, discount, GST_18);

            assertThat(a.subtotal()).isEqualByComparingTo("550.00");
            assertThat(a.discount()).isEqualByComparingTo("82.50");
            assertThat(a.taxableAmount()).isEqualByComparingTo("467.50");
            assertThat(a.taxAmount()).isEqualByComparingTo("84.15");
            assertThat(a.total()).isEqualByComparingTo("551.65");
        }

        @Test
        void discountIsCappedAtSubtotal() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("150.00"), bd("200.00"), GST_18);

            assertThat(a.discount()).isEqualByComparingTo("150.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("0.00");
            assertThat(a.taxAmount()).isEqualByComparingTo("0.00");
            assertThat(a.total()).isEqualByComparingTo("0.00");
        }

        @Test
        void negativeDiscountIsRejected() {
            assertThatThrownBy(() -> BillCalculator.compute(prices("150.00"), bd("-1"), GST_18))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void taxRoundsHalfUpToTwoDecimals() {
            // 0.25 x 18% = 0.045 -> 0.05 (HALF_EVEN would give 0.04)
            assertThat(BillCalculator.compute(prices("0.25"), null, GST_18).taxAmount()).isEqualByComparingTo("0.05");
            // 2.50 x 5% = 0.125 -> 0.13
            assertThat(BillCalculator.compute(prices("2.50"), null, bd("5")).taxAmount()).isEqualByComparingTo("0.13");
        }

        @Test
        void usesTheOutletTaxRateGiven() {
            BillCalculator.Amounts zeroTax = BillCalculator.compute(prices("250.00"), null, bd("0"));
            BillCalculator.Amounts fivePercent = BillCalculator.compute(prices("250.00"), null, bd("5.00"));

            assertThat(zeroTax.total()).isEqualByComparingTo("250.00");
            assertThat(fivePercent.taxAmount()).isEqualByComparingTo("12.50");
            assertThat(fivePercent.total()).isEqualByComparingTo("262.50");
        }

        @Test
        void allAmountsHaveScaleTwo() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("250"), bd("10"), bd("18"));

            assertThat(List.of(a.subtotal(), a.discount(), a.taxableAmount(), a.taxAmount(), a.total()))
                    .allSatisfy(v -> assertThat(v.scale()).isEqualTo(2));
        }
    }

    @Nested
    class BarberCommission {

        @Test
        void commissionIsPriceTimesBarberPercent() {
            assertThat(BillCalculator.commission(bd("250.00"), bd("37.50"))).isEqualByComparingTo("93.75");
            assertThat(BillCalculator.commission(bd("150.00"), bd("32.50"))).isEqualByComparingTo("48.75");
            assertThat(BillCalculator.commission(bd("2500.00"), bd("45.00"))).isEqualByComparingTo("1125.00");
        }

        @Test
        void commissionRoundsHalfUp() {
            // 155 x 33.33% = 51.6615 -> 51.66
            assertThat(BillCalculator.commission(bd("155.00"), bd("33.33"))).isEqualByComparingTo("51.66");
            // 0.10 x 45% = 0.045 -> 0.05
            assertThat(BillCalculator.commission(bd("0.10"), bd("45"))).isEqualByComparingTo("0.05");
        }

        @Test
        void commissionIsOnItemPriceNotOnDiscountedAmount() {
            // Demo rule: a bill discount does not change commission. Commission only sees the item price.
            BigDecimal price = bd("350.00");
            BillCalculator.Amounts discounted = BillCalculator.compute(List.of(price), bd("100"), GST_18);

            assertThat(discounted.taxableAmount()).isEqualByComparingTo("250.00");
            assertThat(BillCalculator.commission(price, bd("40"))).isEqualByComparingTo("140.00");
        }
    }
}
