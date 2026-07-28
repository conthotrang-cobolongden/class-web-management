package com.classmanagement.attendance.dto;

import java.util.List;

// GET /api/sessions/{id}/attendance — {session: {...}, items: [...]}
public record AttendanceRosterResponse(SessionDto session, List<AttendanceRosterItem> items) {
}
