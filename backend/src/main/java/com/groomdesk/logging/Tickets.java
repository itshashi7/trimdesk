package com.groomdesk.logging;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Ticket numbers tie one API request to its log lines. Format {@code TD-20261120-7F3A9C}: product
 * prefix, the IST business date, 6 random hex characters. Short enough for a cashier to read out
 * over the phone; searching it in the host's logs shows everything that request did.
 */
public final class Tickets {

    /** Response header carrying the ticket on every API response. */
    public static final String HEADER = "X-Ticket-Id";
    /** MDC key used by the log pattern and by error responses. */
    public static final String MDC_KEY = "ticket";

    static final Pattern FORMAT = Pattern.compile("TD-\\d{8}-[0-9A-F]{6}");

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tickets() {
    }

    public static String next(Clock clock) {
        String day = LocalDate.now(clock.withZone(IST)).format(DAY);
        return "TD-" + day + "-" + String.format("%06X", RANDOM.nextInt(1 << 24));
    }

    /** Ticket of the request being handled on this thread, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
