package com.classmanagement.reports.dto;

// 1 dong thong ke chuyen can cua HS (FR-4.4, FR-7.1) — dung chung cho items va frequent_absentees.
// attendance_rate = (present + late) / total_sessions * 100, lam tron 2 so le (don vi %).
public record AttendanceStatsItem(Long studentId, String fullName,
                                  long present, long absent, long late, long excused,
                                  long totalSessions, double attendanceRate) {
}
