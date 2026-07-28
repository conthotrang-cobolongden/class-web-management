package com.classmanagement.attendance.dto;

// FR-4.4: thong ke chuyen can 1 HS trong lop.
// attendance_rate = (present + late) / total_sessions * 100, lam tron 2 so le; null khi chua co buoi nao.
public record AttendanceStatsItem(Long studentId, String fullName, long present, long absent,
                                  long late, long excused, long totalSessions, Double attendanceRate) {
}
