package com.classmanagement.reports.dto;

import java.util.List;

// GET /api/reports/attendance (FR-7.1): tong quan + tung HS + danh sach vang nhieu (>= threshold)
public record AttendanceReportResponse(Summary summary,
                                       List<AttendanceStatsItem> items,
                                       List<AttendanceStatsItem> frequentAbsentees) {

    // avg_rate = trung binh attendance_rate cua cac HS trong lop (%, 2 so le)
    public record Summary(long totalSessions, double avgRate) {
    }
}
