package com.classmanagement.reports.dto;

import java.util.Map;

// GET /api/reports/dashboard (FR-7.4, admin only).
// classes_by_grade: key la so khoi dang chuoi ("6".."9"), luon co du 4 khoi (0 neu khong co lop active).
// "Thang nay" tinh theo gio VN (NFR-7).
public record DashboardResponse(long activeStudents,
                                Map<String, Long> classesByGrade,
                                long newStudentsThisMonth,
                                long leftStudents,
                                long unpaidFeesThisMonth) {
}
