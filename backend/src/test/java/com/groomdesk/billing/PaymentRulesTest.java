package com.groomdesk.billing;

import com.groomdesk.billing.PaymentRules.InvalidPaymentException;
import com.groomdesk.billing.PaymentRules.Part;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.groomdesk.billing.PaymentMode.CARD;
import static com.groomdesk.billing.PaymentMode.CASH;
import static com.groomdesk.billing.PaymentMode.MIXED;
import static com.groomdesk.billing.PaymentMode.UPI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRulesTest {

    private static final BigDecimal TOTAL = new BigDecimal("500.00");

    private static Part part(PaymentMode mode, String amount) {
        return new Part(mode, new BigDecimal(amount));
    }

    @Test
    void singleModeBillStoresNoParts() {
        assertThat(PaymentRules.check(TOTAL, UPI, null)).isEmpty();
        assertThat(PaymentRules.check(TOTAL, CASH, List.of())).isEmpty();
    }

    @Test
    void singleModeBillRejectsParts() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, CASH, List.of(part(CASH, "500"))))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void cashPlusUpiThatAddsUpIsAccepted() {
        List<Part> parts = PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "100"), part(UPI, "400")));

        assertThat(parts).extracting(Part::mode).containsExactly(CASH, UPI);
        assertThat(parts.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(parts.get(0).amount().scale()).isEqualTo(2);
    }

    @Test
    void threeWaySplitIsAccepted() {
        assertThat(PaymentRules.check(TOTAL, MIXED,
                List.of(part(CASH, "100"), part(UPI, "250.50"), part(CARD, "149.50")))).hasSize(3);
    }

    @Test
    void splitMustAddUpExactly() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "100"), part(UPI, "399.99"))))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("499.99").hasMessageContaining("500.00");
    }

    @Test
    void splitNeedsTwoOrThreeParts() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "500"))))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, null))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void sameModeTwiceIsRejected() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "100"), part(CASH, "400"))))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void zeroNegativeAndMixedPartsAreRejected() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "0"), part(UPI, "500"))))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "-100"), part(UPI, "600"))))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(MIXED, "100"), part(UPI, "400"))))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void moreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> PaymentRules.check(TOTAL, MIXED, List.of(part(CASH, "100.005"), part(UPI, "399.995"))))
                .isInstanceOf(InvalidPaymentException.class);
    }
}
