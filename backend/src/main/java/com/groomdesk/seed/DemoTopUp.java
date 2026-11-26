package com.groomdesk.seed;

import com.groomdesk.billing.BillNumberGenerator;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import com.groomdesk.seed.BillHistoryGenerator.BillRow;
import com.groomdesk.seed.BillHistoryGenerator.ItemRow;
import com.groomdesk.seed.BillHistoryGenerator.OutletProfile;
import com.groomdesk.seed.BillHistoryGenerator.Sale;
import com.groomdesk.seed.BillHistoryGenerator.SaleLine;
import com.groomdesk.seed.BillHistoryGenerator.WeightedService;
import com.groomdesk.service.GroomingServiceRepository;
import com.groomdesk.staff.Barber;
import com.groomdesk.staff.BarberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Keeps the demo from going stale (docs/SPEC.md §7). With the {@code seed} profile it runs at startup
 * and then every few minutes: past days with almost no bills are filled, and today fills up to the
 * current time (see {@link TopUpPlan}). It only ever adds bills, for customers who already exist.
 *
 * <p>These are made-up bills: never run the seed profile against real data. To switch only the top-up
 * off, set {@code GROOMDESK_SEED_TOP_UP=false}.
 */
@Component
@Profile("seed")
@Order(2)
@EnableScheduling
class DemoTopUp implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTopUp.class);

    private static final int DAYS = 90;
    private static final double USE_PREFERRED_BARBER = 0.80;

    private record OutletDay(long outletId, LocalDate date) {
    }

    private record Regular(long customerId, Long preferredBarberId) {
    }

    /** What is needed to write bills for one outlet. */
    private record Counter(OutletProfile profile, List<Regular> regulars) {
    }

    private final OutletRepository outlets;
    private final BarberRepository barbers;
    private final GroomingServiceRepository services;
    private final BillNumberGenerator billNumbers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean enabled;
    private final Random rnd = new Random();

    DemoTopUp(OutletRepository outlets, BarberRepository barbers, GroomingServiceRepository services,
              BillNumberGenerator billNumbers, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
              Clock clock, @Value("${groomdesk.seed.top-up:true}") boolean enabled) {
        this.outlets = outlets;
        this.barbers = barbers;
        this.services = services;
        this.billNumbers = billNumbers;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        topUp();
    }

    /** Never throws: a failed top-up must not stop the app. The next run tries again. */
    @Scheduled(fixedDelayString = "${groomdesk.seed.top-up-every:PT10M}",
            initialDelayString = "${groomdesk.seed.top-up-every:PT10M}")
    void topUp() {
        if (!enabled) {
            return;
        }
        try {
            int added = addMissingBills(clock.instant());
            if (added > 0) {
                log.info("Demo top-up added {} bills.", added);
            }
        } catch (RuntimeException e) {
            log.error("Demo top-up failed; it will try again on the next run", e);
        }
    }

    private int addMissingBills(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, BillHistoryGenerator.IST);
        LocalDate first = today.minusDays(DAYS - 1L);
        Map<OutletDay, Long> counts = billCountsSince(first);
        List<WeightedService> menu = services.findByActiveTrueOrderByCodeAscNameAsc().stream()
                .map(s -> new WeightedService(s, SeedRunner.popularity(s.getCode())))
                .toList();
        if (menu.isEmpty()) {
            return 0;
        }

        int added = 0;
        for (Outlet outlet : outlets.findAll()) {
            Optional<SeedCatalog.OutletSpec> spec = SeedCatalog.OUTLETS.stream()
                    .filter(s -> s.code().equals(outlet.getCode()))
                    .findFirst();
            if (!outlet.isActive() || spec.isEmpty()) {
                continue;
            }
            Counter counter = null;
            for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
                List<Instant> planned = TopUpPlan.dayTimes(outlet.getCode(), date, spec.get().weekdayMean());
                long seen = counts.getOrDefault(new OutletDay(outlet.getId(), date), 0L);
                if (TopUpPlan.missing(planned, seen, date, now).isEmpty()) {
                    continue;
                }
                if (counter == null) {
                    counter = openCounter(outlet, spec.get().weekdayMean()).orElse(null);
                    if (counter == null) {
                        break;
                    }
                }
                added += fillDay(counter, menu, date, planned, now);
            }
        }
        return added;
    }

    /** One short transaction per outlet and day, under the same outlet lock the billing counter takes. */
    private int fillDay(Counter counter, List<WeightedService> menu, LocalDate date, List<Instant> planned, Instant now) {
        Outlet outlet = counter.profile().outlet();
        Integer added = tx.execute(status -> {
            outlets.findByIdForUpdate(outlet.getId()).orElseThrow();
            // Counted again under the lock: the counter may have billed since the first look.
            Long existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM bill WHERE outlet_id = ? AND created_at >= ? AND created_at < ?",
                    Long.class, outlet.getId(), dayStart(date), dayStart(date.plusDays(1)));
            List<Instant> times = TopUpPlan.missing(planned, existing, date, now);
            if (!times.isEmpty()) {
                insert(counter, menu, times);
            }
            return times.size();
        });
        return added == null ? 0 : added;
    }

    private void insert(Counter counter, List<WeightedService> menu, List<Instant> times) {
        OutletProfile profile = counter.profile();
        Outlet outlet = profile.outlet();
        BillHistoryGenerator generator = new BillHistoryGenerator(rnd, List.of(profile), menu, null);
        List<String> numbers = billNumbers.nextBatch(outlet.getCode(), times.get(0), times.size());
        List<Long> billIds = nextIds("bill", times.size());

        List<BillRow> bills = new ArrayList<>(times.size());
        List<Sale> sales = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            Regular customer = counter.regulars().get(rnd.nextInt(counter.regulars().size()));
            Sale sale = generator.composeSale(profile, barberFor(customer, profile));
            sales.add(sale);
            bills.add(new BillRow(billIds.get(i), numbers.get(i), outlet.getId(), customer.customerId(),
                    profile.cashierId(), sale.amounts(), outlet.getTaxRate(), sale.paymentMode(), times.get(i)));
        }

        List<Long> itemIds = nextIds("bill_item", sales.stream().mapToInt(s -> s.lines().size()).sum());
        List<ItemRow> items = new ArrayList<>(itemIds.size());
        for (int i = 0; i < sales.size(); i++) {
            for (SaleLine line : sales.get(i).lines()) {
                items.add(new ItemRow(itemIds.get(items.size()), billIds.get(i), line.serviceId(), line.barberId(),
                        line.price(), line.commissionAmount()));
            }
        }
        SeedRunner.insertBills(jdbc, bills);
        SeedRunner.insertItems(jdbc, items);
    }

    /** Usually the customer's preferred barber; otherwise whoever is busiest most often. */
    private int barberFor(Regular customer, OutletProfile profile) {
        if (customer.preferredBarberId() != null && rnd.nextDouble() < USE_PREFERRED_BARBER) {
            for (int i = 0; i < profile.barbers().size(); i++) {
                if (profile.barbers().get(i).getId().equals(customer.preferredBarberId())) {
                    return i;
                }
            }
        }
        return BillHistoryGenerator.pickIndex(rnd, profile.barberWeights());
    }

    /** Empty if the outlet has no barber, cashier or past customer to bill. */
    private Optional<Counter> openCounter(Outlet outlet, double weekdayMean) {
        List<Barber> staff = barbers.findByOutletIdAndActiveTrueOrderByName(outlet.getId());
        List<Long> cashiers = jdbc.queryForList(
                "SELECT id FROM app_user WHERE outlet_id = ? AND role = 'CASHIER' ORDER BY id LIMIT 1",
                Long.class, outlet.getId());
        // The demo customer is left out: his story is that he has never visited T. Nagar.
        List<Regular> regulars = jdbc.query("""
                        SELECT c.id, c.preferred_barber_id FROM customer c
                        WHERE c.phone <> ? AND EXISTS (SELECT 1 FROM bill b WHERE b.customer_id = c.id AND b.outlet_id = ?)""",
                (rs, n) -> new Regular(rs.getLong("id"), rs.getObject("preferred_barber_id", Long.class)),
                SeedRunner.DEMO_PHONE, outlet.getId());
        if (staff.isEmpty() || cashiers.isEmpty() || regulars.isEmpty()) {
            return Optional.empty();
        }

        // Each barber keeps the share of the work they had so far.
        Map<Long, Long> done = new HashMap<>();
        jdbc.query("""
                SELECT i.barber_id, COUNT(*) AS services FROM bill_item i JOIN bill b ON b.id = i.bill_id
                WHERE b.outlet_id = ? GROUP BY i.barber_id""",
                rs -> {
                    done.put(rs.getLong("barber_id"), rs.getLong("services"));
                }, outlet.getId());
        double[] weights = staff.stream().mapToDouble(b -> 1 + done.getOrDefault(b.getId(), 0L)).toArray();

        return Optional.of(new Counter(
                new OutletProfile(outlet, weekdayMean, staff, weights, cashiers.get(0)), regulars));
    }

    private Map<OutletDay, Long> billCountsSince(LocalDate first) {
        Map<OutletDay, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT outlet_id, CAST(created_at AT TIME ZONE 'Asia/Kolkata' AS date) AS day, COUNT(*) AS bills
                FROM bill WHERE created_at >= ? GROUP BY outlet_id, day""",
                rs -> {
                    counts.put(new OutletDay(rs.getLong("outlet_id"), rs.getObject("day", LocalDate.class)),
                            rs.getLong("bills"));
                }, dayStart(first));
        return counts;
    }

    /** Takes ids from the table's own sequence, so they never clash with bills saved at the counter. */
    private List<Long> nextIds(String table, int count) {
        return jdbc.queryForList(
                "SELECT nextval(pg_get_serial_sequence(?, 'id')) FROM generate_series(1, ?)",
                Long.class, table, count);
    }

    private static OffsetDateTime dayStart(LocalDate date) {
        return date.atStartOfDay(BillHistoryGenerator.IST).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }
}
