package com.groomdesk.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopUpPlanTest {

    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 24);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    private static Instant ist(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(BillHistoryGenerator.IST).toInstant();
    }

    private static List<Instant> plan(LocalDate date) {
        return TopUpPlan.dayTimes("TNR", date, 45);
    }

    @Test
    void aDaysTimesNeverChange() {
        assertThat(plan(TUESDAY)).isEqualTo(plan(TUESDAY));
        assertThat(plan(TUESDAY)).isNotEqualTo(plan(TUESDAY.plusWeeks(1)));
        assertThat(TopUpPlan.dayTimes("ANR", TUESDAY, 45)).isNotEqualTo(plan(TUESDAY));
    }

    @Test
    void timesAreSortedAndInsideOpeningHoursOfThatDay() {
        assertThat(plan(TUESDAY)).isSorted().allSatisfy(t -> {
            ZonedDateTime local = t.atZone(BillHistoryGenerator.IST);
            assertThat(local.toLocalDate()).isEqualTo(TUESDAY);
            assertThat(local.toLocalTime()).isBetween(LocalTime.of(10, 0), LocalTime.of(20, 59, 59));
        });
    }

    /** Otherwise a filled day would still look like a gap and be filled again on the next run. */
    @Test
    void everyPlannedDayHasAtLeastTheSeedsMinimum() {
        for (int d = 0; d < 365; d++) {
            assertThat(TopUpPlan.dayTimes("KKN", TUESDAY.plusDays(d), 1))
                    .hasSizeGreaterThanOrEqualTo(BillHistoryGenerator.MIN_WEEKDAY_BILLS);
        }
    }

    @Test
    void anEmptyPastDayIsFilledCompletely() {
        assertThat(TopUpPlan.missing(plan(TUESDAY), 0, TUESDAY, ist(TODAY, 12, 0))).isEqualTo(plan(TUESDAY));
    }

    @Test
    void aPastDayWithStrayBillsIsToppedUpToThePlan() {
        List<Instant> added = TopUpPlan.missing(plan(TUESDAY), 4, TUESDAY, ist(TODAY, 12, 0));

        assertThat(added).hasSize(plan(TUESDAY).size() - 4);
    }

    @Test
    void aSecondRunOverAFilledDayAddsNothing() {
        Instant now = ist(TODAY, 12, 0);
        long afterFirstRun = 4 + TopUpPlan.missing(plan(TUESDAY), 4, TUESDAY, now).size();

        assertThat(TopUpPlan.missing(plan(TUESDAY), afterFirstRun, TUESDAY, now)).isEmpty();
    }

    @Test
    void aSeededPastDayIsNeverTouched() {
        long seededMinimum = BillHistoryGenerator.MIN_WEEKDAY_BILLS;

        assertThat(TopUpPlan.missing(plan(TUESDAY), seededMinimum, TUESDAY, ist(TODAY, 12, 0))).isEmpty();
    }

    @Test
    void nothingIsDueTodayBeforeOpening() {
        assertThat(TopUpPlan.missing(plan(TODAY), 0, TODAY, ist(TODAY, 9, 59))).isEmpty();
    }

    @Test
    void todayFillsUpToNowAndNoFurther() {
        Instant now = ist(TODAY, 15, 0);
        long due = plan(TODAY).stream().filter(t -> !t.isAfter(now)).count();

        List<Instant> added = TopUpPlan.missing(plan(TODAY), 0, TODAY, now);

        assertThat(added).hasSize((int) due).allMatch(t -> !t.isAfter(now));
        assertThat(TopUpPlan.missing(plan(TODAY), added.size(), TODAY, now)).isEmpty();
    }

    @Test
    void laterInTheDayOnlyTheNewlyDueBillsAreAdded() {
        Instant noon = ist(TODAY, 12, 0);
        Instant evening = ist(TODAY, 19, 0);
        int byNoon = TopUpPlan.missing(plan(TODAY), 0, TODAY, noon).size();

        List<Instant> added = TopUpPlan.missing(plan(TODAY), byNoon, TODAY, evening);

        assertThat(added).isNotEmpty().allMatch(t -> t.isAfter(noon) && !t.isAfter(evening));
    }

    @Test
    void billsSavedAtTheCounterCountTowardsToday() {
        Instant now = ist(TODAY, 15, 0);
        int due = TopUpPlan.missing(plan(TODAY), 0, TODAY, now).size();

        assertThat(TopUpPlan.missing(plan(TODAY), 3, TODAY, now)).hasSize(due - 3);
        assertThat(TopUpPlan.missing(plan(TODAY), due + 5, TODAY, now)).isEmpty();
    }

    @Test
    void futureDaysAreLeftAlone() {
        assertThat(TopUpPlan.missing(plan(TODAY.plusDays(1)), 0, TODAY.plusDays(1), ist(TODAY, 23, 0))).isEmpty();
    }
}
