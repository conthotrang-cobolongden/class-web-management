package com.classmanagement.classes.dto;

// Phan tu body PUT /api/classes/{id}/schedules: {day_of_week(1=T2..7=CN), start_time, end_time}.
// Gio nhan dang chuoi "HH:mm" va duoc parse + validate trong ClassService
// (bean validation khong cascade vao phan tu List top-level nen check tay).
public record ScheduleItemRequest(Integer dayOfWeek, String startTime, String endTime) {
}
