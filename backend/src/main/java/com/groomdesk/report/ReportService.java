package com.groomdesk.report;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.auth.Role;
import com.groomdesk.billing.PaymentMode;
import com.groomdesk.config.TimeConfig;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reports. Plain SQL aggregates (JdbcTemplate) are the clearest tool for these;
 * every report checks the caller's outlet scope first (docs/SPEC.md §4).
 */
@Service
public class ReportService {

    public record PaymentSplit(PaymentMode mode, long bills, BigDecimal amount) {
    }

    /** revenue = Σ item price (tax-inclusive, before bill discount); commission = Σ commission_amount. */
    public record BarberEarnings(long barberId, String name, long services, BigDecimal revenue, BigDecimal commission) {
    }

    /** revenue = Σ bill total (what customers paid, tax included). */
    public record OutletDayReport(long outletId, String outletCode, String outletName, String outletArea, LocalDate date,
                                  long bills, BigDecimal revenue, BigDecimal averageBill, BigDecimal discounts,
                                  BigDecimal taxIncluded, List<PaymentSplit> payments, List<BarberEarnings> barbers) {
    }

    /** One bill as a CSV row. Cash, UPI and card hold what was paid in each mode (split bills fill several). */
    public record BillRow(String billNo, Instant createdAt, String customerName, String customerPhone,
                          String cashierName, String items, BigDecimal subtotal, BigDecimal discount,
                          BigDecimal taxAmount, BigDecimal total, PaymentMode paymentMode,
                          BigDecimal cash, BigDecimal upi, BigDecimal card) {
    }

    private static final List<PaymentMode> MODE_ORDER = List.of(PaymentMode.UPI, PaymentMode.CASH, PaymentMode.CARD);

    private final JdbcTemplate jdbc;
    private final OutletRepository outlets;
    private final AccessScope access;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, OutletRepository outlets, AccessScope access, Clock clock) {
        this.jdbc = jdbc;
        this.outlets = outlets;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OutletDayReport outletDay(CurrentUser user, long outletId, LocalDate date) {
        access.requireAnyRole(user, Role.OUTLET_MANAGER, Role.FRANCHISE_OWNER, Role.HQ_ADMIN);
        Outlet outlet = outlets.findById(outletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outlet not found"));
        access.requireOutlet(user, outlet);

        OffsetDateTime from = dayStart(date);
        OffsetDateTime to = dayStart(date.plusDays(1));

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS bills, COALESCE(SUM(total), 0) AS revenue,
                       COALESCE(SUM(discount), 0) AS discounts, COALESCE(SUM(tax_amount), 0) AS tax
                FROM bill WHERE outlet_id = ? AND created_at >= ? AND created_at < ?""", outletId, from, to);
        long bills = ((Number) totals.get("bills")).longValue();
        BigDecimal revenue = money(totals.get("revenue"));

        // Single-mode bills count their whole total; split (MIXED) bills count each part in its own mode,
        // so the three amounts always add up to revenue. A split bill counts once in each mode it used.
        Map<PaymentMode, PaymentSplit> byMode = new EnumMap<>(PaymentMode.class);
        jdbc.query("""
                SELECT mode, COUNT(DISTINCT bill_id) AS bills, SUM(amount) AS amount
                FROM (SELECT b.id AS bill_id, b.payment_mode AS mode, b.total AS amount
                      FROM bill b
                      WHERE b.outlet_id = ? AND b.created_at >= ? AND b.created_at < ? AND b.payment_mode <> 'MIXED'
                      UNION ALL
                      SELECT b.id, p.mode, p.amount
                      FROM bill b JOIN bill_payment p ON p.bill_id = b.id
                      WHERE b.outlet_id = ? AND b.created_at >= ? AND b.created_at < ? AND b.payment_mode = 'MIXED') x
                GROUP BY mode""", rs -> {
            PaymentMode mode = PaymentMode.valueOf(rs.getString("mode"));
            byMode.put(mode, new PaymentSplit(mode, rs.getLong("bills"), money(rs.getBigDecimal("amount"))));
        }, outletId, from, to, outletId, from, to);
        List<PaymentSplit> payments = MODE_ORDER.stream()
                .map(m -> byMode.getOrDefault(m, new PaymentSplit(m, 0, money(BigDecimal.ZERO))))
                .toList();

        // Every barber of the outlet appears, including those with no services that day.
        List<BarberEarnings> barbers = jdbc.query("""
                SELECT br.id, br.name, COUNT(x.id) AS services,
                       COALESCE(SUM(x.price), 0) AS revenue, COALESCE(SUM(x.commission_amount), 0) AS commission
                FROM barber br
                LEFT JOIN (SELECT i.id, i.barber_id, i.price, i.commission_amount
                           FROM bill_item i JOIN bill b ON b.id = i.bill_id
                           WHERE b.outlet_id = ? AND b.created_at >= ? AND b.created_at < ?) x ON x.barber_id = br.id
                WHERE br.outlet_id = ?
                GROUP BY br.id, br.name
                ORDER BY revenue DESC, br.name""",
                (rs, n) -> new BarberEarnings(rs.getLong("id"), rs.getString("name"), rs.getLong("services"),
                        money(rs.getBigDecimal("revenue")), money(rs.getBigDecimal("commission"))),
                outletId, from, to, outletId);

        BigDecimal average = bills == 0 ? money(BigDecimal.ZERO)
                : revenue.divide(BigDecimal.valueOf(bills), 2, RoundingMode.HALF_UP);
        return new OutletDayReport(outlet.getId(), outlet.getCode(), outlet.getName(), outlet.getArea(), date,
                bills, revenue, average, money(totals.get("discounts")), money(totals.get("tax")), payments, barbers);
    }

    /**
     * Every bill of one outlet on one IST date, oldest first, for CSV export. Cashiers may export their
     * own outlet's bills for today only; managers, owners and HQ any date of an outlet they can see.
     */
    @Transactional(readOnly = true)
    public List<BillRow> outletDayBills(CurrentUser user, long outletId, LocalDate date) {
        Outlet outlet = outlets.findById(outletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outlet not found"));
        access.requireOutlet(user, outlet);
        if (user.role() == Role.CASHIER && !date.equals(LocalDate.now(clock.withZone(TimeConfig.BUSINESS_ZONE)))) {
            throw new AccessDeniedException("Cashiers can export today's bills only");
        }
        return jdbc.query("""
                SELECT b.bill_no, b.created_at, c.name AS customer_name, c.phone AS customer_phone,
                       u.full_name AS cashier_name, b.subtotal, b.discount, b.tax_amount, b.total, b.payment_mode,
                       (SELECT string_agg(s.name || ' (' || br.name || ')', '; ' ORDER BY i.id)
                        FROM bill_item i JOIN service s ON s.id = i.service_id JOIN barber br ON br.id = i.barber_id
                        WHERE i.bill_id = b.id) AS items,
                       CASE WHEN b.payment_mode = 'MIXED' THEN COALESCE((SELECT SUM(p.amount) FROM bill_payment p
                                WHERE p.bill_id = b.id AND p.mode = 'CASH'), 0)
                            WHEN b.payment_mode = 'CASH' THEN b.total ELSE 0 END AS cash,
                       CASE WHEN b.payment_mode = 'MIXED' THEN COALESCE((SELECT SUM(p.amount) FROM bill_payment p
                                WHERE p.bill_id = b.id AND p.mode = 'UPI'), 0)
                            WHEN b.payment_mode = 'UPI' THEN b.total ELSE 0 END AS upi,
                       CASE WHEN b.payment_mode = 'MIXED' THEN COALESCE((SELECT SUM(p.amount) FROM bill_payment p
                                WHERE p.bill_id = b.id AND p.mode = 'CARD'), 0)
                            WHEN b.payment_mode = 'CARD' THEN b.total ELSE 0 END AS card
                FROM bill b
                JOIN customer c ON c.id = b.customer_id
                JOIN app_user u ON u.id = b.cashier_id
                WHERE b.outlet_id = ? AND b.created_at >= ? AND b.created_at < ?
                ORDER BY b.created_at, b.id""",
                (rs, n) -> new BillRow(rs.getString("bill_no"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("customer_name"), rs.getString("customer_phone"), rs.getString("cashier_name"),
                        rs.getString("items"), money(rs.getBigDecimal("subtotal")), money(rs.getBigDecimal("discount")),
                        money(rs.getBigDecimal("tax_amount")), money(rs.getBigDecimal("total")),
                        PaymentMode.valueOf(rs.getString("payment_mode")), money(rs.getBigDecimal("cash")),
                        money(rs.getBigDecimal("upi")), money(rs.getBigDecimal("card"))),
                outletId, dayStart(date), dayStart(date.plusDays(1)));
    }

    /** The business day runs midnight to midnight in IST; bills are stored in UTC. */
    private static OffsetDateTime dayStart(LocalDate date) {
        return date.atStartOfDay(TimeConfig.BUSINESS_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static BigDecimal money(Object value) {
        BigDecimal v = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
