package com.groomdesk.report;

import com.groomdesk.config.TimeConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Business dates are IST calendar days; bills are stored in UTC. */
final class BusinessDays {

    private BusinessDays() {
    }

    /** Midnight IST at the start of the date, as a UTC timestamp. */
    static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(TimeConfig.BUSINESS_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** Exclusive end of an inclusive date range: midnight IST after the last day. */
    static OffsetDateTime endOf(LocalDate inclusiveLastDay) {
        return startOf(inclusiveLastDay.plusDays(1));
    }
}
