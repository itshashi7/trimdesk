package com.groomdesk.billing;

import com.groomdesk.config.TimeConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * {@code {OUTLET_CODE}-{YYYYMMDD}-{4-digit daily sequence}}, date in IST, e.g. MNP-20261121-0042.
 * Callers must hold the outlet row lock (see OutletRepository#findByIdForUpdate).
 */
@Component
public class BillNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    static final int MAX_DAILY_SEQUENCE = 9999;

    private final BillRepository bills;

    public BillNumberGenerator(BillRepository bills) {
        this.bills = bills;
    }

    public String next(String outletCode, Instant now) {
        String prefix = prefix(outletCode, now);
        return format(prefix, nextSequence(bills.findMaxBillNoWithPrefix(prefix), prefix));
    }

    static String prefix(String outletCode, Instant now) {
        return outletCode + "-" + now.atZone(TimeConfig.BUSINESS_ZONE).toLocalDate().format(DATE) + "-";
    }

    static String format(String prefix, int sequence) {
        return prefix + "%04d".formatted(sequence);
    }

    static int nextSequence(String currentMax, String prefix) {
        if (currentMax == null) {
            return 1;
        }
        int next = Integer.parseInt(currentMax.substring(prefix.length())) + 1;
        if (next > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("Daily bill sequence exhausted for " + prefix);
        }
        return next;
    }
}
