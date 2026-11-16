package com.groomdesk.billing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillCalculatorTest {

    private static final BigDecimal GST_18 = new BigDecimal("18.00");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static List<BigDecimal> prices(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    /** Menu prices include tax: the customer pays subtotal − discount; tax is backed out of that. */
    @Nested
    class BillTotal {

        @Test
        void totalIsSubtotalAndTaxIsInsideIt() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("250.00", "150.00"), null, GST_18);

            // 400 x 100 / 118 = 338.983 -> 338.98; tax = 400 - 338.98
            assertThat(a.subtotal()).isEqualByComparingTo("400.00");
            assertThat(a.discount()).isEqualByComparingTo("0.00");
            assertThat(a.total()).isEqualByComparingTo("400.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("338.98");
            assertThat(a.taxAmount()).isEqualByComparingTo("61.02");
        }

        @Test
        void taxableAndTaxAlwaysAddUpToTotal() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("350.00", "80.00", "600.00"), bd("37.50"), GST_18);

            assertThat(a.taxableAmount().add(a.taxAmount())).isEqualByComparingTo(a.total());
        }

        @Test
        void flatDiscountReducesWhatTheCustomerPays() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("250.00", "150.00"), bd("100"), GST_18);

            // 300 x 100 / 118 = 254.237 -> 254.24
            assertThat(a.discount()).isEqualByComparingTo("100.00");
            assertThat(a.total()).isEqualByComparingTo("300.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("254.24");
            assertThat(a.taxAmount()).isEqualByComparingTo("45.76");
        }

        @Test
        void percentDiscountIsTakenOnSubtotal() {
            List<BigDecimal> items = prices("150.00", "150.00", "250.00");
            BigDecimal discount = BillCalculator.percentOf(BillCalculator.subtotal(items), bd("15"));

            BillCalculator.Amounts a = BillCalculator.compute(items, discount, GST_18);

            // 550 - 82.50 = 467.50; 467.50 x 100 / 118 = 396.186 -> 396.19
            assertThat(a.subtotal()).isEqualByComparingTo("550.00");
            assertThat(a.discount()).isEqualByComparingTo("82.50");
            assertThat(a.total()).isEqualByComparingTo("467.50");
            assertThat(a.taxableAmount()).isEqualByComparingTo("396.19");
            assertThat(a.taxAmount()).isEqualByComparingTo("71.31");
        }

        @Test
        void discountIsCappedAtSubtotal() {
            BillCalculator.Amounts a = BillCalculator.compute(prices("150.00"), bd("200.00"), GST_18);

            assertThat(a.discount()).isEqualByComparingTo("150.00");
            assertThat(a.total()).isEqualByComparingTo("0.00");
            assertThat(a.taxableAmount()).isEqualByComparingTo("0.00");
            assertThat(a.taxAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        void negativeDiscountIsRejected() {
            assertThatThrownBy(() -> BillCalculator.compute(prices("150.00"), bd("-1"), GST_18))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void taxableRoundsHalfUpToTwoDecimals() {
            // 0.04 x 100 / 160 = 0.025 -> 0.03 (HALF_EVEN would give 0.02)
            BillCalculator.Amounts a = BillCalculator.compute(prices("0.04"), null, bd("60"));

            assertThat(a.taxableAmount()).isEqualByComparingTo("0.03");
            assertThat(a.taxAmount()).isEqualByComparingTo("0.01");
        }

        @Test
        void usesTheOutletTaxRateGiven() {
            BillCalculator.Amounts zeroTax = BillCalculator.compute(prices("250.00"), null, bd("0"));
            BillCalculator.Amounts fivePercent = BillCalculator.compute(prices("250.00"), null, bd("5.00"));

            assertThat(zeroTax.total()).isEqualByComparingTo("250.00");
            assertThat(zeroTax.taxAmount()).isEqualByComparingTo("0.00");
            // 250 x 100 / 105 = 238.095 -> 238.10
            assertThat(fivePercent.total()).isEqualByComparingTo("250.00");
            assertThat(fivePercent.taxableAmount()).isEqualByComparingTo("238.10");
            assertThat(fivePercent.taxAmount()).isEqualByComparingTo("11.90");
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

            assertThat(discounted.total()).isEqualByComparingTo("250.00");
            assertThat(BillCalculator.commission(price, bd("40"))).isEqualByComparingTo("140.00");
        }
    }
}
