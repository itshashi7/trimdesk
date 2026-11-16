package com.groomdesk.billing;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillNumberGeneratorTest {

    @Test
    void dateIsTheIndianBusinessDate() {
        // 19:00 UTC on 20 Nov is 00:30 IST on 21 Nov
        assertThat(BillNumberGenerator.prefix("MNP", Instant.parse("2026-11-20T19:00:00Z"))).isEqualTo("MNP-20261121-");
        assertThat(BillNumberGenerator.prefix("MNP", Instant.parse("2026-11-20T18:29:59Z"))).isEqualTo("MNP-20261120-");
    }

    @Test
    void firstBillOfTheDayIsSequenceOne() {
        assertThat(BillNumberGenerator.nextSequence(null, "MNP-20261121-")).isEqualTo(1);
        assertThat(BillNumberGenerator.format("MNP-20261121-", 1)).isEqualTo("MNP-20261121-0001");
    }

    @Test
    void nextSequenceFollowsTheHighestExisting() {
        assertThat(BillNumberGenerator.nextSequence("MNP-20261121-0041", "MNP-20261121-")).isEqualTo(42);
        assertThat(BillNumberGenerator.format("MNP-20261121-", 42)).isEqualTo("MNP-20261121-0042");
    }

    @Test
    void sequenceIsCappedAtFourDigits() {
        assertThatThrownBy(() -> BillNumberGenerator.nextSequence("MNP-20261121-9999", "MNP-20261121-"))
                .isInstanceOf(IllegalStateException.class);
    }
}
