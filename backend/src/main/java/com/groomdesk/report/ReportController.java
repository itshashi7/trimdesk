package com.groomdesk.report;

import com.groomdesk.auth.CurrentUser;
import com.groomdesk.report.HqDashboardService.HqSummary;
import com.groomdesk.report.ReportService.BillRow;
import com.groomdesk.report.ReportService.OutletDayReport;
import com.groomdesk.report.RoyaltyService.RoyaltyReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;
    private final HqDashboardService dashboard;
    private final RoyaltyService royalty;

    public ReportController(ReportService reports, HqDashboardService dashboard, RoyaltyService royalty) {
        this.reports = reports;
        this.dashboard = dashboard;
        this.royalty = royalty;
    }

    /** Revenue, bill count, payment split and per-barber earnings for one outlet on one IST date. */
    @GetMapping("/outlet/{id}/day")
    public OutletDayReport outletDay(CurrentUser user, @PathVariable long id,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.outletDay(user, id, date);
    }

    /** Every bill of one outlet on one IST date, for CSV export (cashiers: today only). */
    @GetMapping("/outlet/{id}/bills")
    public List<BillRow> outletDayBills(CurrentUser user, @PathVariable long id,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.outletDayBills(user, id, date);
    }

    /** Totals, today so far, daily trend, outlet ranking and top services for an inclusive IST date range. */
    @GetMapping("/hq/summary")
    public HqSummary hqSummary(CurrentUser user,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboard.summary(user, from, to);
    }

    /** Royalty due per franchise for a calendar month (YYYY-MM, IST). */
    @GetMapping("/royalty")
    public RoyaltyReport royalty(CurrentUser user, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return royalty.monthly(user, month);
    }
}
