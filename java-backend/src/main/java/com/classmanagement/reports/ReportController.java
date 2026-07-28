package com.classmanagement.reports;

import com.classmanagement.reports.dto.AttendanceReportResponse;
import com.classmanagement.reports.dto.DashboardResponse;
import com.classmanagement.reports.dto.RevenueReportResponse;
import com.classmanagement.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Bao cao & dashboard (FR-7) — quyen chan trong ReportService (NFR-2)
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // FR-7.1: chuyen can theo lop — member cua lop
    @GetMapping("/attendance")
    public AttendanceReportResponse attendance(@RequestParam(name = "class_id", required = false) Long classId,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) String threshold,
                                               @AuthenticationPrincipal User me) {
        return reportService.attendanceReport(me, classId, from, to, threshold);
    }

    // FR-7.3: doanh thu thang — admin toan trung tam / GV chinh lop minh / tro giang 403
    @GetMapping("/revenue")
    public RevenueReportResponse revenue(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month,
                                         @RequestParam(name = "class_id", required = false) Long classId,
                                         @AuthenticationPrincipal User me) {
        return reportService.revenueReport(me, year, month, classId);
    }

    // FR-7.4: dashboard tong quan — chi admin
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal User me) {
        return reportService.dashboard(me);
    }
}
