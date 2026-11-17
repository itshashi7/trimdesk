package com.groomdesk.report;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.auth.Role;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HQ dashboard (CLAUDE.md §5/§6): totals, a live "today so far" figure, daily revenue trend,
 * outlet ranking and top services. HQ sees every outlet; a franchise owner sees their outlets.
 * Revenue is Σ bill total (what customers paid, tax included).
 */
@Service
public class HqDashboardService {

    public record Totals(BigDecimal revenue, long bills, BigDecimal averageBill, long customers,
                         int activeOutlets, int totalOutlets) {
    }

    /** Live figures for the current IST day, refreshed by the dashboard. */
    public record Today(LocalDate date, BigDecimal revenue, long bills, int outletsBilling) {
    }

    public record TrendPoint(LocalDate date, BigDecimal revenue, long bills) {
    }

    public record OutletRank(long outletId, String code, String name, String franchiseName, BigDecimal revenue,
                             long bills, BigDecimal averageBill, long customers) {
    }

    /** revenue = Σ service price (before bill discounts). */
    public record TopService(long serviceId, String name, String category, long count, BigDecimal revenue) {
    }

    public record HqSummary(LocalDate from, LocalDate to, Totals totals, Today today, List<TrendPoint> trend,
                            List<OutletRank> outlets, List<TopService> topServices) {
    }

    private static final int MAX_RANGE_DAYS = 366;
    private static final int TOP_SERVICES = 8;

    private final JdbcTemplate jdbc;
    private final OutletRepository outlets;
    private final AccessScope access;
    private final Clock clock;

    public HqDashboardService(JdbcTemplate jdbc, OutletRepository outlets, AccessScope access, Clock clock) {
        this.jdbc = jdbc;
        this.outlets = outlets;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HqSummary summary(CurrentUser user, LocalDate from, LocalDate to) {
        access.requireAnyRole(user, Role.HQ_ADMIN, Role.FRANCHISE_OWNER);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a range of at most 366 days");
        }

        Long[] scope = outlets.findAll().stream()
                .filter(o -> o.isActive() && access.canAccessOutlet(user, o))
                .map(Outlet::getId)
                .toArray(Long[]::new);
        OffsetDateTime start = BusinessDays.startOf(from);
        OffsetDateTime end = BusinessDays.endOf(to);

        Map<String, Object> t = jdbc.queryForMap("""
                SELECT COUNT(*) AS bills, COALESCE(SUM(total), 0) AS revenue,
                       COUNT(DISTINCT customer_id) AS customers, COUNT(DISTINCT outlet_id) AS active
                FROM bill WHERE outlet_id = ANY(?) AND created_at >= ? AND created_at < ?""", scope, start, end);
        long bills = number(t.get("bills"));
        BigDecimal revenue = money(t.get("revenue"));
        Totals totals = new Totals(revenue, bills, average(revenue, bills), number(t.get("customers")),
                (int) number(t.get("active")), scope.length);

        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.BUSINESS_ZONE));
        Map<String, Object> d = jdbc.queryForMap("""
                SELECT COUNT(*) AS bills, COALESCE(SUM(total), 0) AS revenue, COUNT(DISTINCT outlet_id) AS active
                FROM bill WHERE outlet_id = ANY(?) AND created_at >= ? AND created_at < ?""",
                scope, BusinessDays.startOf(today), BusinessDays.endOf(today));
        Today todayFigures = new Today(today, money(d.get("revenue")), number(d.get("bills")), (int) number(d.get("active")));

        Map<LocalDate, TrendPoint> byDay = new HashMap<>();
        jdbc.query("""
                SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS day, SUM(total) AS revenue, COUNT(*) AS bills
                FROM bill WHERE outlet_id = ANY(?) AND created_at >= ? AND created_at < ?
                GROUP BY 1""", rs -> {
            LocalDate day = rs.getObject("day", LocalDate.class);
            byDay.put(day, new TrendPoint(day, money(rs.getBigDecimal("revenue")), rs.getLong("bills")));
        }, scope, start, end);
        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            trend.add(byDay.getOrDefault(day, new TrendPoint(day, money(BigDecimal.ZERO), 0)));
        }

        List<OutletRank> ranking = jdbc.query("""
                SELECT o.id, o.code, o.name, f.name AS franchise, COUNT(b.id) AS bills,
                       COALESCE(SUM(b.total), 0) AS revenue, COUNT(DISTINCT b.customer_id) AS customers
                FROM outlet o JOIN franchise f ON f.id = o.franchise_id
                LEFT JOIN bill b ON b.outlet_id = o.id AND b.created_at >= ? AND b.created_at < ?
                WHERE o.id = ANY(?)
                GROUP BY o.id, o.code, o.name, f.name
                ORDER BY revenue DESC, o.name""", (rs, n) -> {
            BigDecimal r = money(rs.getBigDecimal("revenue"));
            long b = rs.getLong("bills");
            return new OutletRank(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("franchise"), r, b, average(r, b), rs.getLong("customers"));
        }, start, end, scope);

        List<TopService> topServices = jdbc.query("""
                SELECT s.id, s.name, s.category, COUNT(i.id) AS cnt, SUM(i.price) AS revenue
                FROM bill_item i JOIN bill b ON b.id = i.bill_id JOIN service s ON s.id = i.service_id
                WHERE b.outlet_id = ANY(?) AND b.created_at >= ? AND b.created_at < ?
                GROUP BY s.id, s.name, s.category
                ORDER BY revenue DESC, s.name
                LIMIT ?""", (rs, n) -> new TopService(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                rs.getLong("cnt"), money(rs.getBigDecimal("revenue"))), scope, start, end, TOP_SERVICES);

        return new HqSummary(from, to, totals, todayFigures, trend, ranking, topServices);
    }

    private static BigDecimal average(BigDecimal revenue, long bills) {
        return bills == 0 ? money(BigDecimal.ZERO) : revenue.divide(BigDecimal.valueOf(bills), 2, RoundingMode.HALF_UP);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal money(Object value) {
        BigDecimal v = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
