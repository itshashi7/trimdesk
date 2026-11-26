package com.groomdesk.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Decides which demo bills are missing. The bill times for an outlet and date are always the same, so
 * running the top-up again adds nothing until more of them are due.
 */
final class TopUpPlan {

    /** The seeded history ends on this upward trend; topped-up days carry on from it. */
    private static final double TREND = 1.08;

    private TopUpPlan() {
    }

    /** The full day's bill times for one outlet, sorted. */
    static List<Instant> dayTimes(String outletCode, LocalDate date, double weekdayMean) {
        // Neighbouring dates give neighbouring hashes; SplittableRandom spreads them into unrelated seeds.
        long seed = new SplittableRandom(Objects.hash(outletCode, date.toEpochDay())).nextLong();
        return BillHistoryGenerator.dayTimes(new Random(seed), date, weekdayMean, TREND);
    }

    /**
     * Times of the bills to add for one outlet and date.
     *
     * <ul>
     *   <li>A past day is filled only if it is a gap: fewer bills than any seeded day has. A filled day
     *       is no longer a gap, and seeded history is never touched.</li>
     *   <li>Today is filled up to {@code now}, counting the bills it already has.</li>
     * </ul>
     *
     * @param planned  the day's times from {@link #dayTimes}
     * @param existing bills the outlet already has on that date
     */
    static List<Instant> missing(List<Instant> planned, long existing, LocalDate date, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, BillHistoryGenerator.IST);
        if (date.isAfter(today) || (date.isBefore(today) && existing >= BillHistoryGenerator.MIN_WEEKDAY_BILLS)) {
            return List.of();
        }
        List<Instant> due = planned.stream().filter(t -> !t.isAfter(now)).toList();
        long shortfall = due.size() - existing;
        // The latest due times, so that new bills look recent.
        return shortfall <= 0 ? List.of() : due.subList(due.size() - (int) shortfall, due.size());
    }
}
