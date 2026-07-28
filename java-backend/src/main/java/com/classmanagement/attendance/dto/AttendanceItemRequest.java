package com.classmanagement.attendance.dto;

// Mot phan tu items khi bulk upsert diem danh (PUT /api/sessions/{id}/attendance)
public record AttendanceItemRequest(Long studentId, String status, String note) {
}
