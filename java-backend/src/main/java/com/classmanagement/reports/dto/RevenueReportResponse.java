package com.classmanagement.reports.dto;

import java.math.BigDecimal;
import java.util.List;

// GET /api/reports/revenue (FR-7.3): doanh thu thang theo lop + tong cong
public record RevenueReportResponse(List<RevenueItem> items,
                                    BigDecimal grandTotalPaid,
                                    BigDecimal grandTotalDue) {

    // total_due = tong COALESCE(adjusted_amount, base_amount); total_paid = tong payments;
    // cac count dem theo status khoan phi (unpaid/partial/paid)
    public record RevenueItem(Long classId, String className,
                              BigDecimal totalDue, BigDecimal totalPaid,
                              long unpaidCount, long partialCount, long paidCount) {
    }
}
