package com.classmanagement.attendance.dto;

import java.time.Instant;

// Mot dong trong bang diem danh: HS dang hoc lop tai ngay buoi hoc,
// status/note/recorded_* = null neu chua co ban ghi diem danh (FR-4.1)
public record AttendanceRosterItem(Long studentId, String fullName, String status, String note,
                                   String recordedByName, Instant recordedAt) {
}
