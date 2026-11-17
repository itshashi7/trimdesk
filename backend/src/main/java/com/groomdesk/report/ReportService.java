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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reports. Plain SQL aggregates (JdbcTemplate) are the clearest tool for these;
 * every report checks the caller's outlet scope first (CLAUDE.md §4).
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

    private static final List<PaymentMode> MODE_ORDER = List.of(PaymentMode.UPI, PaymentMode.CASH, PaymentMode.CARD);

    private final JdbcTemplate jdbc;
    private final OutletRepository outlets;
    private final AccessScope access;

    public ReportService(JdbcTemplate jdbc, OutletRepository outlets, AccessScope access) {
        this.jdbc = jdbc;
        this.outlets = outlets;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public OutletDayReport outletDay(CurrentUser user, long outletId, LocalDate date) {
        access.requireAnyRole(user, Role.OUTLET_MANAGER, Role.FRANCHISE_OWNER, Role.HQ_ADMIN);
        Outlet outlet = outlets.findById(outletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outlet not found"));
        access.requireOutlet(user, outlet);

        // The business day runs midnight to midnight in IST; bills are stored in UTC.
        OffsetDateTime from = date.atStartOfDay(TimeConfig.BUSINESS_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = date.plusDays(1).atStartOfDay(TimeConfig.BUSINESS_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS bills, COALESCE(SUM(total), 0) AS revenue,
                       COALESCE(SUM(discount), 0) AS discounts, COALESCE(SUM(tax_amount), 0) AS tax
                FROM bill WHERE outlet_id = ? AND created_at >= ? AND created_at < ?""", outletId, from, to);
        long bills = ((Number) totals.get("bills")).longValue();
        BigDecimal revenue = money(totals.get("revenue"));

        Map<PaymentMode, PaymentSplit> byMode = new EnumMap<>(PaymentMode.class);
        jdbc.query("""
                SELECT payment_mode, COUNT(*) AS bills, SUM(total) AS amount
                FROM bill WHERE outlet_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY payment_mode""", rs -> {
            PaymentMode mode = PaymentMode.valueOf(rs.getString("payment_mode"));
            byMode.put(mode, new PaymentSplit(mode, rs.getLong("bills"), money(rs.getBigDecimal("amount"))));
        }, outletId, from, to);
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

    private static BigDecimal money(Object value) {
        BigDecimal v = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
