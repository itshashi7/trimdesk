package com.groomdesk.seed;

import com.groomdesk.billing.BillCalculator;
import com.groomdesk.billing.PaymentMode;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.service.GroomingService;
import com.groomdesk.service.ServiceCategory;
import com.groomdesk.staff.Barber;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Builds the bill history in memory with explicit ids, ready for JDBC batch insert.
 *
 * <ol>
 *   <li>Bill slots: per outlet per day, 25-60 on weekdays (outlet-specific level, mild upward trend),
 *       +40% on weekends, times weighted to the 5-9 pm peak.</li>
 *   <li>Customers are planned until every slot has a visit: 60% one-time, 40% repeat (2-15 visits);
 *       a quarter of repeat customers also visit another outlet, usually in the same city.</li>
 *   <li>Each customer's visits are spread across the window and matched to slots in time order.</li>
 * </ol>
 */
final class BillHistoryGenerator {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final int[] HOURS = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    private static final double[] HOUR_WEIGHTS = {3, 5, 6, 5, 4, 4, 6, 10, 12, 12, 9};
    private static final double[] ITEM_COUNT_WEIGHTS = {55, 33, 12};
    /** Visit count weights for repeat customers, index 0 = 2 visits ... index 13 = 15 visits. */
    private static final double[] REPEAT_VISIT_WEIGHTS = {24, 18, 14, 11, 9, 7, 5, 4, 3, 2, 1.5, 1, 0.8, 0.7};
    private static final PaymentMode[] MODES = {PaymentMode.UPI, PaymentMode.CASH, PaymentMode.CARD};
    private static final double[] MODE_WEIGHTS = {65, 30, 5};
    private static final int[] FLAT_DISCOUNTS = {50, 100, 150, 200};
    private static final int[] PERCENT_DISCOUNTS = {10, 15, 20};

    /** No seeded day has fewer bills than this at any outlet; the demo top-up relies on it. */
    static final int MIN_WEEKDAY_BILLS = 25;
    private static final int MAX_WEEKDAY_BILLS = 60;
    private static final double WEEKEND_UPLIFT = 1.4;
    private static final double ONE_TIME_SHARE = 0.60;
    private static final double MULTI_OUTLET_SHARE = 0.25;
    private static final double SAME_CITY_PREFERENCE = 0.80;
    private static final double PREFERRED_BARBER_SHARE = 0.70;
    private static final double USE_PREFERRED_BARBER = 0.80;
    private static final double SAME_BARBER_PER_BILL = 0.85;
    private static final double DISCOUNT_SHARE = 0.10;

    private static final DateTimeFormatter BILL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    record OutletProfile(Outlet outlet, double weekdayMean, List<Barber> barbers, double[] barberWeights,
                         long cashierId) {
    }

    record WeightedService(GroomingService service, double weight) {
    }

    record CustomerRow(long id, String phone, String name, String notes, Long preferredBarberId,
                       long firstVisitOutletId, Instant createdAt) {
    }

    record BillRow(long id, String billNo, long outletId, long customerId, long cashierId,
                   BillCalculator.Amounts amounts, BigDecimal taxRate, PaymentMode paymentMode, Instant createdAt) {
    }

    record ItemRow(long id, long billId, long serviceId, long barberId, BigDecimal price,
                   BigDecimal commissionAmount) {
    }

    record Result(List<CustomerRow> customers, List<BillRow> bills, List<ItemRow> items) {
    }

    record SaleLine(long serviceId, long barberId, BigDecimal price, BigDecimal commissionAmount) {
    }

    /** What one bill contains, before it gets an id, a number, a customer and a time. */
    record Sale(List<SaleLine> lines, BillCalculator.Amounts amounts, PaymentMode paymentMode) {
    }

    private record Slot(int outlet, Instant at, String billNo) {
    }

    private record Ticket(int customer, double position) {
    }

    private record Placed(Slot slot, int customer) {
    }

    private static final class PlannedCustomer {
        final int home;
        final List<Integer> visitOutlets = new ArrayList<>();
        int preferredBarber = -1;
        Instant firstVisit;
        int firstOutlet;

        PlannedCustomer(int home) {
            this.home = home;
        }
    }

    private final Random rnd;
    private final List<OutletProfile> outlets;
    private final List<WeightedService> services;
    private final SeedCatalog.People people;

    BillHistoryGenerator(Random rnd, List<OutletProfile> outlets, List<WeightedService> services,
                         SeedCatalog.People people) {
        this.rnd = rnd;
        this.outlets = outlets;
        this.services = services;
        this.people = people;
    }

    /**
     * @param endDate last day (IST) of the window
     * @param cutoff  drop bills after this instant (so "today" stops at now); null keeps full days
     */
    Result generate(LocalDate endDate, int days, Instant cutoff) {
        List<List<Slot>> slots = buildSlots(endDate, days, cutoff);
        List<PlannedCustomer> planned = planCustomers(slots);
        int[][] assigned = assignVisits(slots, planned);
        long[] customerIds = new long[planned.size()];
        List<CustomerRow> customers = buildCustomers(planned, customerIds);

        List<Placed> placed = new ArrayList<>();
        for (int o = 0; o < slots.size(); o++) {
            for (int i = 0; i < slots.get(o).size(); i++) {
                placed.add(new Placed(slots.get(o).get(i), assigned[o][i]));
            }
        }
        placed.sort(Comparator.comparing((Placed p) -> p.slot().at()).thenComparing(p -> p.slot().billNo()));

        List<BillRow> bills = new ArrayList<>(placed.size());
        List<ItemRow> items = new ArrayList<>(placed.size() * 2);
        long billId = 0;
        long itemId = 0;
        for (Placed p : placed) {
            billId++;
            OutletProfile outlet = outlets.get(p.slot().outlet());
            PlannedCustomer customer = planned.get(p.customer());

            int billBarber = customer.preferredBarber >= 0 && customer.home == p.slot().outlet()
                    && rnd.nextDouble() < USE_PREFERRED_BARBER
                    ? customer.preferredBarber
                    : pickIndex(rnd, outlet.barberWeights());

            Sale sale = composeSale(outlet, billBarber);
            for (SaleLine line : sale.lines()) {
                items.add(new ItemRow(++itemId, billId, line.serviceId(), line.barberId(), line.price(),
                        line.commissionAmount()));
            }

            bills.add(new BillRow(billId, p.slot().billNo(), outlet.outlet().getId(), customerIds[p.customer()],
                    outlet.cashierId(), sale.amounts(), outlet.outlet().getTaxRate(), sale.paymentMode(), p.slot().at()));
        }
        return new Result(customers, bills, items);
    }

    /** One bill's services, barbers, discount and payment mode. Also used by {@link DemoTopUp}. */
    Sale composeSale(OutletProfile outlet, int billBarber) {
        List<SaleLine> lines = new ArrayList<>(3);
        List<BigDecimal> prices = new ArrayList<>(3);
        for (GroomingService service : pickServices(1 + pickIndex(rnd, ITEM_COUNT_WEIGHTS))) {
            int barberIdx = rnd.nextDouble() < SAME_BARBER_PER_BILL ? billBarber : pickIndex(rnd, outlet.barberWeights());
            Barber barber = outlet.barbers().get(barberIdx);
            BigDecimal price = service.getPrice();
            prices.add(price);
            lines.add(new SaleLine(service.getId(), barber.getId(), price,
                    BillCalculator.commission(price, barber.getCommissionPercent())));
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (rnd.nextDouble() < DISCOUNT_SHARE) {
            discount = rnd.nextBoolean()
                    ? BigDecimal.valueOf(FLAT_DISCOUNTS[rnd.nextInt(FLAT_DISCOUNTS.length)])
                    : BillCalculator.percentOf(BillCalculator.subtotal(prices),
                    BigDecimal.valueOf(PERCENT_DISCOUNTS[rnd.nextInt(PERCENT_DISCOUNTS.length)]));
        }
        BillCalculator.Amounts amounts = BillCalculator.compute(prices, discount, outlet.outlet().getTaxRate());
        return new Sale(lines, amounts, MODES[pickIndex(rnd, MODE_WEIGHTS)]);
    }

    /**
     * Sorted bill times for one outlet on one IST date: 25-60 on a weekday, +40% at weekends, peaking
     * 5-9 pm. Also used by {@link TopUpPlan}.
     */
    static List<Instant> dayTimes(Random rnd, LocalDate date, double weekdayMean, double trend) {
        DayOfWeek dow = date.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        double expected = weekdayMean * trend * (0.85 + 0.30 * rnd.nextDouble());
        expected = Math.max(MIN_WEEKDAY_BILLS, Math.min(MAX_WEEKDAY_BILLS, expected));
        if (weekend) {
            expected *= WEEKEND_UPLIFT;
        }
        int count = (int) Math.round(expected);

        List<Instant> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int hour = HOURS[pickIndex(rnd, HOUR_WEIGHTS)];
            times.add(date.atTime(hour, rnd.nextInt(60), rnd.nextInt(60)).atZone(IST).toInstant());
        }
        Collections.sort(times);
        return times;
    }

    private List<List<Slot>> buildSlots(LocalDate endDate, int days, Instant cutoff) {
        List<List<Slot>> byOutlet = new ArrayList<>();
        for (int o = 0; o < outlets.size(); o++) {
            byOutlet.add(new ArrayList<>());
        }
        LocalDate start = endDate.minusDays(days - 1L);
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            double trend = days > 1 ? 0.92 + 0.16 * d / (days - 1) : 1.0;
            String datePart = date.format(BILL_DATE);

            for (int o = 0; o < outlets.size(); o++) {
                OutletProfile profile = outlets.get(o);
                int seq = 0;
                for (Instant at : dayTimes(rnd, date, profile.weekdayMean(), trend)) {
                    if (cutoff != null && at.isAfter(cutoff)) {
                        break;
                    }
                    seq++;
                    String billNo = "%s-%s-%04d".formatted(profile.outlet().getCode(), datePart, seq);
                    byOutlet.get(o).add(new Slot(o, at, billNo));
                }
            }
        }
        return byOutlet;
    }

    /** Creates customers round-robin per home outlet until every outlet's slots are covered exactly. */
    private List<PlannedCustomer> planCustomers(List<List<Slot>> slots) {
        int[] remaining = new int[outlets.size()];
        for (int o = 0; o < remaining.length; o++) {
            remaining[o] = slots.get(o).size();
        }
        List<PlannedCustomer> planned = new ArrayList<>();
        boolean open = true;
        while (open) {
            open = false;
            for (int o = 0; o < remaining.length; o++) {
                if (remaining[o] > 0) {
                    open = true;
                    planned.add(planCustomer(o, remaining));
                }
            }
        }
        return planned;
    }

    private PlannedCustomer planCustomer(int home, int[] remaining) {
        PlannedCustomer c = new PlannedCustomer(home);
        int visits = rnd.nextDouble() < ONE_TIME_SHARE ? 1 : 2 + pickIndex(rnd, REPEAT_VISIT_WEIGHTS);
        int crossVisits = visits >= 2 && rnd.nextDouble() < MULTI_OUTLET_SHARE
                ? Math.min(visits - 1, 1 + rnd.nextInt(2))
                : 0;

        int homeVisits = Math.min(visits - crossVisits, remaining[home]);
        remaining[home] -= homeVisits;
        for (int i = 0; i < homeVisits; i++) {
            c.visitOutlets.add(home);
        }
        for (int i = 0; i < crossVisits; i++) {
            int other = pickOtherOutlet(home, remaining);
            if (other < 0) {
                break;
            }
            remaining[other]--;
            c.visitOutlets.add(other);
        }
        if (c.visitOutlets.size() >= 3 && rnd.nextDouble() < PREFERRED_BARBER_SHARE) {
            c.preferredBarber = pickIndex(rnd, outlets.get(home).barberWeights());
        }
        return c;
    }

    private int pickOtherOutlet(int home, int[] remaining) {
        String city = outlets.get(home).outlet().getCity();
        List<Integer> sameCity = new ArrayList<>();
        List<Integer> elsewhere = new ArrayList<>();
        for (int o = 0; o < outlets.size(); o++) {
            if (o == home || remaining[o] == 0) {
                continue;
            }
            (city.equals(outlets.get(o).outlet().getCity()) ? sameCity : elsewhere).add(o);
        }
        boolean useSameCity = !sameCity.isEmpty() && (elsewhere.isEmpty() || rnd.nextDouble() < SAME_CITY_PREFERENCE);
        List<Integer> pool = useSameCity ? sameCity : elsewhere;
        return pool.isEmpty() ? -1 : pool.get(rnd.nextInt(pool.size()));
    }

    /** Spreads each customer's visits over the window, then matches them to slots in time order. */
    private int[][] assignVisits(List<List<Slot>> slots, List<PlannedCustomer> planned) {
        List<List<Ticket>> tickets = new ArrayList<>();
        for (int o = 0; o < outlets.size(); o++) {
            tickets.add(new ArrayList<>());
        }
        for (int ci = 0; ci < planned.size(); ci++) {
            List<Integer> order = new ArrayList<>(planned.get(ci).visitOutlets);
            Collections.shuffle(order, rnd);
            int visits = order.size();
            for (int k = 0; k < visits; k++) {
                tickets.get(order.get(k)).add(new Ticket(ci, (k + rnd.nextDouble()) / visits));
            }
        }

        int[][] assigned = new int[outlets.size()][];
        for (int o = 0; o < outlets.size(); o++) {
            List<Ticket> outletTickets = tickets.get(o);
            List<Slot> outletSlots = slots.get(o);
            if (outletTickets.size() != outletSlots.size()) {
                throw new IllegalStateException("Visit plan mismatch at outlet " + o);
            }
            outletTickets.sort(Comparator.comparingDouble(Ticket::position));
            assigned[o] = new int[outletSlots.size()];
            for (int i = 0; i < outletSlots.size(); i++) {
                int ci = outletTickets.get(i).customer();
                assigned[o][i] = ci;
                PlannedCustomer c = planned.get(ci);
                Instant at = outletSlots.get(i).at();
                if (c.firstVisit == null || at.isBefore(c.firstVisit)) {
                    c.firstVisit = at;
                    c.firstOutlet = o;
                }
            }
        }
        return assigned;
    }

    /** Customer ids follow first-visit order, so ids grow with time like real sign-ups. */
    private List<CustomerRow> buildCustomers(List<PlannedCustomer> planned, long[] customerIds) {
        List<Integer> order = new ArrayList<>(planned.size());
        for (int i = 0; i < planned.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> planned.get(i).firstVisit).thenComparing(i -> i));

        List<CustomerRow> rows = new ArrayList<>(planned.size());
        for (int rank = 0; rank < order.size(); rank++) {
            int ci = order.get(rank);
            long id = rank + 1L;
            customerIds[ci] = id;
            PlannedCustomer c = planned.get(ci);
            Long preferredBarberId = c.preferredBarber >= 0
                    ? outlets.get(c.home).barbers().get(c.preferredBarber).getId()
                    : null;
            double noteChance = c.visitOutlets.size() >= 3 ? 0.20 : 0.03;
            String notes = rnd.nextDouble() < noteChance ? people.note() : null;
            rows.add(new CustomerRow(id, people.phone(), people.fullName(), notes, preferredBarberId,
                    outlets.get(c.firstOutlet).outlet().getId(), c.firstVisit));
        }
        return rows;
    }

    /**
     * Distinct services by popularity. A combo already covers hair and beard, so a bill never
     * mixes a combo with separate hair/beard items or with a second combo.
     */
    private List<GroomingService> pickServices(int count) {
        double[] weights = services.stream().mapToDouble(WeightedService::weight).toArray();
        List<GroomingService> chosen = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            if (Arrays.stream(weights).noneMatch(w -> w > 0)) {
                break;
            }
            int idx = pickIndex(rnd, weights);
            GroomingService service = services.get(idx).service();
            chosen.add(service);
            weights[idx] = 0;
            ServiceCategory cat = service.getCategory();
            for (int i = 0; i < weights.length; i++) {
                ServiceCategory other = services.get(i).service().getCategory();
                boolean conflict = cat == ServiceCategory.COMBO
                        ? other == ServiceCategory.COMBO || other == ServiceCategory.HAIR || other == ServiceCategory.BEARD
                        : (cat == ServiceCategory.HAIR || cat == ServiceCategory.BEARD) && other == ServiceCategory.COMBO;
                if (conflict) {
                    weights[i] = 0;
                }
            }
        }
        return chosen;
    }

    static int pickIndex(Random rnd, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        if (total <= 0) {
            throw new IllegalArgumentException("No positive weight");
        }
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return i;
            }
        }
        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0) {
                return i;
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
