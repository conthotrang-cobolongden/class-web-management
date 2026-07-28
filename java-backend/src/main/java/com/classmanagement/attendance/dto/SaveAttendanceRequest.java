package com.classmanagement.attendance.dto;

import java.util.List;

// Body PUT /api/sessions/{id}/attendance: {items: [{student_id, status, note?}]}
public record SaveAttendanceRequest(List<AttendanceItemRequest> items) {
}
