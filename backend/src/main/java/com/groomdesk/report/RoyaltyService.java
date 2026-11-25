package com.groomdesk.report;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.auth.Role;
import com.groomdesk.franchise.RoyaltyCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly royalty (docs/SPEC.md §3): per franchise, Σ taxable_amount of its outlets × royalty % / 100,
 * rounded once on the franchise's monthly sum. HQ sees every franchise; an owner sees their own.
 */
@Service
public class RoyaltyService {

    public record RoyaltyOutlet(long outletId, String code, String name, long bills, BigDecimal revenue,
                                BigDecimal taxable) {
    }

    public record RoyaltyFranchise(long franchiseId, String name, String ownerName, BigDecimal royaltyPercent,
                                   long bills, BigDecimal revenue, BigDecimal taxable, BigDecimal royaltyDue,
                                   List<RoyaltyOutlet> outlets) {
    }

    public record RoyaltyReport(String month, LocalDate from, LocalDate to, List<RoyaltyFranchise> franchises,
                                BigDecimal totalRevenue, BigDecimal totalTaxable, BigDecimal totalRoyalty) {
    }

    private static final String SQL = """
            SELECT f.id AS fid, f.name AS fname, f.owner_name, f.royalty_percent,
                   o.id AS oid, o.code, o.name AS oname,
                   COUNT(b.id) AS bills, COALESCE(SUM(b.total), 0) AS revenue, COALESCE(SUM(b.taxable_amount), 0) AS taxable
            FROM franchise f JOIN outlet o ON o.franchise_id = f.id
            LEFT JOIN bill b ON b.outlet_id = o.id AND b.created_at >= ? AND b.created_at < ?
            %s
            GROUP BY f.id, f.name, f.owner_name, f.royalty_percent, o.id, o.code, o.name
            ORDER BY f.name, revenue DESC, o.name""";

    private final JdbcTemplate jdbc;
    private final AccessScope access;

    public RoyaltyService(JdbcTemplate jdbc, AccessScope access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private record Row(long fid, String fname, String owner, BigDecimal pct, RoyaltyOutlet outlet) {
    }

    @Transactional(readOnly = true)
    public RoyaltyReport monthly(CurrentUser user, YearMonth month) {
        access.requireAnyRole(user, Role.HQ_ADMIN, Role.FRANCHISE_OWNER);
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        boolean owner = user.role() == Role.FRANCHISE_OWNER;

        Object[] args = owner
                ? new Object[]{BusinessDays.startOf(first), BusinessDays.endOf(last), user.franchiseId()}
                : new Object[]{BusinessDays.startOf(first), BusinessDays.endOf(last)};
        List<Row> rows = jdbc.query(SQL.formatted(owner ? "WHERE f.id = ?" : ""), (rs, n) -> new Row(
                rs.getLong("fid"), rs.getString("fname"), rs.getString("owner_name"), rs.getBigDecimal("royalty_percent"),
                new RoyaltyOutlet(rs.getLong("oid"), rs.getString("code"), rs.getString("oname"), rs.getLong("bills"),
                        money(rs.getBigDecimal("revenue")), money(rs.getBigDecimal("taxable")))), args);

        Map<Long, List<Row>> byFranchise = new LinkedHashMap<>();
        rows.forEach(r -> byFranchise.computeIfAbsent(r.fid(), k -> new ArrayList<>()).add(r));

        List<RoyaltyFranchise> franchises = new ArrayList<>();
        for (List<Row> group : byFranchise.values()) {
            Row head = group.get(0);
            List<RoyaltyOutlet> outletRows = group.stream().map(Row::outlet).toList();
            long bills = outletRows.stream().mapToLong(RoyaltyOutlet::bills).sum();
            BigDecimal revenue = sum(outletRows.stream().map(RoyaltyOutlet::revenue).toList());
            BigDecimal taxable = sum(outletRows.stream().map(RoyaltyOutlet::taxable).toList());
            franchises.add(new RoyaltyFranchise(head.fid(), head.fname(), head.owner(), head.pct(), bills, revenue,
                    taxable, RoyaltyCalculator.royaltyDue(taxable, head.pct()), outletRows));
        }

        return new RoyaltyReport(month.toString(), first, last, franchises,
                sum(franchises.stream().map(RoyaltyFranchise::revenue).toList()),
                sum(franchises.stream().map(RoyaltyFranchise::taxable).toList()),
                sum(franchises.stream().map(RoyaltyFranchise::royaltyDue).toList()));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
