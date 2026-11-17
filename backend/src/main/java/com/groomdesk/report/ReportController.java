package com.groomdesk.report;

import com.groomdesk.auth.CurrentUser;
import com.groomdesk.report.ReportService.OutletDayReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Revenue, bill count, payment split and per-barber earnings for one outlet on one IST date. */
    @GetMapping("/outlet/{id}/day")
    public OutletDayReport outletDay(CurrentUser user, @PathVariable long id,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.outletDay(user, id, date);
    }
}
