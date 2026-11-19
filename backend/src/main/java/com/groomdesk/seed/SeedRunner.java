package com.groomdesk.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomdesk.auth.AppUser;
import com.groomdesk.auth.AppUserRepository;
import com.groomdesk.auth.Role;
import com.groomdesk.franchise.Franchise;
import com.groomdesk.franchise.FranchiseRepository;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import com.groomdesk.seed.BillHistoryGenerator.BillRow;
import com.groomdesk.seed.BillHistoryGenerator.CustomerRow;
import com.groomdesk.seed.BillHistoryGenerator.ItemRow;
import com.groomdesk.seed.BillHistoryGenerator.OutletProfile;
import com.groomdesk.seed.BillHistoryGenerator.WeightedService;
import com.groomdesk.service.GroomingService;
import com.groomdesk.service.GroomingServiceRepository;
import com.groomdesk.service.ServiceCategory;
import com.groomdesk.staff.Barber;
import com.groomdesk.staff.BarberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo data loader (CLAUDE.md §7). Runs only with the {@code seed} profile and does nothing if
 * franchises already exist.
 *
 * <p>Default: 90 days ending today (IST), with today cut off at the current time. Because the cutoff
 * moves, a default re-seed differs slightly. Pin {@code groomdesk.seed.end-date=YYYY-MM-DD} (full days,
 * no cutoff) for byte-identical data from the fixed random seed.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed -Dspring-boot.run.arguments=--groomdesk.seed.end-date=2026-11-16
 * </pre>
 * In PowerShell, quote each {@code -D...} argument.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private static final long RANDOM_SEED = 20261121L;
    private static final int DAYS = 90;
    private static final int BATCH_SIZE = 1000;
    private static final String DEMO_PASSWORD = "demo123";
    /** Placeholder only. Confirm the real GST rate with the client's accountant before real use. */
    private static final BigDecimal PLACEHOLDER_TAX_RATE = new BigDecimal("18.00");
    /** Most-used barber gets more chairs-time, so per-barber earnings differ. */
    private static final double[] BARBER_WEIGHTS = {1.3, 1.0, 0.7};

    record ServiceSeed(String code, String name, ServiceCategory category, BigDecimal price, int durationMin) {
    }

    private final FranchiseRepository franchises;
    private final OutletRepository outlets;
    private final BarberRepository barbers;
    private final GroomingServiceRepository services;
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final String endDateProperty;

    public SeedRunner(FranchiseRepository franchises, OutletRepository outlets, BarberRepository barbers,
                      GroomingServiceRepository services, AppUserRepository users, JdbcTemplate jdbc,
                      PasswordEncoder passwordEncoder, ObjectMapper objectMapper,
                      PlatformTransactionManager transactionManager,
                      @Value("${groomdesk.seed.end-date:}") String endDateProperty) {
        this.franchises = franchises;
        this.outlets = outlets;
        this.barbers = barbers;
        this.services = services;
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.endDateProperty = endDateProperty;
    }

    @Override
    public void run(String... args) {
        if (franchises.count() > 0) {
            log.info("Seed skipped: data already present ({} franchises).", franchises.count());
            return;
        }
        long started = System.currentTimeMillis();
        tx.executeWithoutResult(status -> seed());
        log.info("Seed finished in {} s.", (System.currentTimeMillis() - started) / 1000.0);
        logSummary();
    }

    private void seed() {
        Random rnd = new Random(RANDOM_SEED);
        SeedCatalog.People people = new SeedCatalog.People(rnd);

        List<Franchise> savedFranchises = new ArrayList<>();
        for (SeedCatalog.FranchiseSpec spec : SeedCatalog.FRANCHISES) {
            Franchise f = new Franchise(spec.name(), spec.ownerName(), people.phone(), spec.gstin(), spec.royaltyPercent());
            f.setCreatedAt(LocalDate.of(spec.foundedYear(), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28))
                    .atStartOfDay(BillHistoryGenerator.IST).toInstant());
            savedFranchises.add(f);
        }
        franchises.saveAll(savedFranchises);

        List<Outlet> savedOutlets = new ArrayList<>();
        for (SeedCatalog.OutletSpec spec : SeedCatalog.OUTLETS) {
            savedOutlets.add(new Outlet(savedFranchises.get(spec.franchise()), spec.code(), spec.name(), spec.area(),
                    spec.city(), PLACEHOLDER_TAX_RATE));
        }
        outlets.saveAll(savedOutlets);

        List<List<Barber>> barbersByOutlet = new ArrayList<>();
        for (Outlet outlet : savedOutlets) {
            List<Barber> outletBarbers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                BigDecimal commission = BigDecimal.valueOf(30 + 0.5 * rnd.nextInt(31)).setScale(2);
                outletBarbers.add(new Barber(outlet, people.barberName(), people.phone(), commission));
            }
            barbers.saveAll(outletBarbers);
            barbersByOutlet.add(outletBarbers);
        }

        List<GroomingService> savedServices = loadServices();
        services.saveAll(savedServices);

        Map<String, Long> cashierIdByOutlet = createUsers(savedFranchises, savedOutlets, people);
        users.flush();

        List<OutletProfile> profiles = new ArrayList<>();
        for (int o = 0; o < savedOutlets.size(); o++) {
            Outlet outlet = savedOutlets.get(o);
            List<Double> weights = new ArrayList<>(List.of(BARBER_WEIGHTS[0], BARBER_WEIGHTS[1], BARBER_WEIGHTS[2]));
            Collections.shuffle(weights, rnd);
            profiles.add(new OutletProfile(outlet, SeedCatalog.OUTLETS.get(o).weekdayMean(), barbersByOutlet.get(o),
                    weights.stream().mapToDouble(Double::doubleValue).toArray(), cashierIdByOutlet.get(outlet.getCode())));
        }
        List<WeightedService> weighted = savedServices.stream()
                .map(s -> new WeightedService(s, popularity(s.getCode())))
                .toList();

        LocalDate endDate;
        Instant cutoff;
        if (endDateProperty == null || endDateProperty.isBlank()) {
            endDate = LocalDate.now(BillHistoryGenerator.IST);
            cutoff = Instant.now();
        } else {
            endDate = LocalDate.parse(endDateProperty);
            cutoff = null;
        }
        log.info("Generating {} days of bills ending {} (IST)...", DAYS, endDate);
        BillHistoryGenerator.Result result =
                new BillHistoryGenerator(rnd, profiles, weighted, people).generate(endDate, DAYS, cutoff);

        insertCustomers(result.customers());
        insertBills(result.bills());
        insertItems(result.items());
        for (String table : List.of("customer", "bill", "bill_item")) {
            // Rows were inserted with explicit ids; move identity sequences past them for the API.
            jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), (SELECT MAX(id) FROM " + table + "))");
        }
        pinDemoCustomer();
    }

    /** Phone number the billing screen and the demo script use; see CustomerPanel's DEMO_PHONE. */
    static final String DEMO_PHONE = "9910551967";

    /**
     * The generated customers change with the seed end date, but the demo always needs the same
     * phone number to show cross-outlet history at the T. Nagar counter. Give that number (and the
     * name Ajith Raj) to the busiest customer who has visited 2+ other outlets and never T. Nagar.
     */
    private void pinDemoCustomer() {
        // Free the number if a generated customer happens to hold it (phone is unique).
        jdbc.update("UPDATE customer SET phone = '6' || lpad(id::text, 9, '0') WHERE phone = ?", DEMO_PHONE);
        List<Long> picks = jdbc.queryForList("""
                SELECT c.id
                FROM customer c
                JOIN bill b ON b.customer_id = c.id
                JOIN outlet o ON o.id = b.outlet_id
                GROUP BY c.id
                HAVING COUNT(DISTINCT b.outlet_id) FILTER (WHERE o.code <> 'TNR') >= 2
                   AND COUNT(*) FILTER (WHERE o.code = 'TNR') = 0
                ORDER BY COUNT(DISTINCT b.outlet_id) DESC, COUNT(*) DESC, c.id
                LIMIT 1""", Long.class);
        if (picks.isEmpty()) {
            log.warn("No suitable demo customer found; {} is not assigned", DEMO_PHONE);
            return;
        }
        jdbc.update("UPDATE customer SET phone = ?, name = 'Ajith Raj' WHERE id = ?", DEMO_PHONE, picks.get(0));
        log.info("Demo customer: {} Ajith Raj (customer id {})", DEMO_PHONE, picks.get(0));
    }

    private List<GroomingService> loadServices() {
        try (InputStream in = new ClassPathResource("seed/services.json").getInputStream()) {
            List<ServiceSeed> seeds = objectMapper.readValue(in, new TypeReference<List<ServiceSeed>>() {
            });
            return seeds.stream()
                    .map(s -> new GroomingService(s.code(), s.name(), s.category(), s.price(), s.durationMin()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed/services.json", e);
        }
    }

    /** Shortcut 1 is the most common service; items without a shortcut are rare. */
    private static double popularity(String code) {
        if (code == null || !code.matches("\\d")) {
            return 1.5;
        }
        return Math.max(3, 36 - 4 * Integer.parseInt(code));
    }

    /** hq, owner1-4, manager_&lt;code&gt;, cashier_&lt;code&gt;; all with password demo123. */
    private Map<String, Long> createUsers(List<Franchise> savedFranchises, List<Outlet> savedOutlets,
                                          SeedCatalog.People people) {
        List<AppUser> all = new ArrayList<>();
        all.add(user("hq", "Meena Sundaram", Role.HQ_ADMIN, null, null));
        for (int i = 0; i < savedFranchises.size(); i++) {
            Franchise f = savedFranchises.get(i);
            all.add(user("owner" + (i + 1), f.getOwnerName(), Role.FRANCHISE_OWNER, f, null));
        }
        List<AppUser> cashiers = new ArrayList<>();
        for (Outlet outlet : savedOutlets) {
            String code = outlet.getCode().toLowerCase();
            all.add(user("manager_" + code, people.fullName(), Role.OUTLET_MANAGER, null, outlet));
            AppUser cashier = user("cashier_" + code, people.fullName(), Role.CASHIER, null, outlet);
            all.add(cashier);
            cashiers.add(cashier);
        }
        users.saveAll(all);

        Map<String, Long> cashierIdByOutlet = new LinkedHashMap<>();
        for (AppUser cashier : cashiers) {
            cashierIdByOutlet.put(cashier.getOutlet().getCode(), cashier.getId());
        }
        return cashierIdByOutlet;
    }

    private AppUser user(String username, String fullName, Role role, Franchise franchise, Outlet outlet) {
        return new AppUser(username, passwordEncoder.encode(DEMO_PASSWORD), fullName, role, franchise, outlet);
    }

    private void insertCustomers(List<CustomerRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO customer (id, phone, name, notes, preferred_barber_id, first_visit_outlet_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", rows, BATCH_SIZE, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.phone());
            ps.setString(3, c.name());
            ps.setString(4, c.notes());
            if (c.preferredBarberId() == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, c.preferredBarberId());
            }
            ps.setLong(6, c.firstVisitOutletId());
            ps.setObject(7, utc(c.createdAt()));
        });
    }

    private void insertBills(List<BillRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO bill (id, bill_no, outlet_id, customer_id, cashier_id, subtotal, discount, taxable_amount,
                                  tax_rate, tax_amount, total, payment_mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", rows, BATCH_SIZE, (ps, b) -> {
            ps.setLong(1, b.id());
            ps.setString(2, b.billNo());
            ps.setLong(3, b.outletId());
            ps.setLong(4, b.customerId());
            ps.setLong(5, b.cashierId());
            ps.setBigDecimal(6, b.amounts().subtotal());
            ps.setBigDecimal(7, b.amounts().discount());
            ps.setBigDecimal(8, b.amounts().taxableAmount());
            ps.setBigDecimal(9, b.taxRate());
            ps.setBigDecimal(10, b.amounts().taxAmount());
            ps.setBigDecimal(11, b.amounts().total());
            ps.setString(12, b.paymentMode().name());
            ps.setObject(13, utc(b.createdAt()));
        });
    }

    private void insertItems(List<ItemRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO bill_item (id, bill_id, service_id, barber_id, price, commission_amount)
                VALUES (?, ?, ?, ?, ?, ?)""", rows, BATCH_SIZE, (ps, i) -> {
            ps.setLong(1, i.id());
            ps.setLong(2, i.billId());
            ps.setLong(3, i.serviceId());
            ps.setLong(4, i.barberId());
            ps.setBigDecimal(5, i.price());
            ps.setBigDecimal(6, i.commissionAmount());
        });
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private void logSummary() {
        for (String table : List.of("franchise", "outlet", "barber", "service", "app_user", "customer", "bill", "bill_item")) {
            log.info("  {} rows: {}", table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        log.info("Demo logins (password {}): hq, owner1-owner4, manager_<code>, cashier_<code>", DEMO_PASSWORD);
        log.info("Repeat customers who visited 2+ outlets (good for the demo):");
        jdbc.query("""
                SELECT c.name, c.phone, COUNT(*) AS visits, COUNT(DISTINCT b.outlet_id) AS outlets
                FROM customer c JOIN bill b ON b.customer_id = c.id
                GROUP BY c.id, c.name, c.phone
                HAVING COUNT(DISTINCT b.outlet_id) >= 2
                ORDER BY visits DESC, c.id
                LIMIT 5""", rs -> {
            log.info("  {} {} - {} visits across {} outlets",
                    rs.getString("phone"), rs.getString("name"), rs.getLong("visits"), rs.getLong("outlets"));
        });
    }
}
