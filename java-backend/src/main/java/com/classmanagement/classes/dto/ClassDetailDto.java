package com.classmanagement.classes.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Chi tiet lop (GET /api/classes/{id} + body tra ve cua POST/PUT):
// {class: {..., staff: [{user_id, full_name, staff_role}], schedules: [{id, day_of_week, start_time, end_time}]}}
public record ClassDetailDto(Long id, String name, Integer grade, String schoolYear,
                             LocalDate startDate, LocalDate endDate, BigDecimal defaultMonthlyFee,
                             String status, String note, Instant createdAt, Instant updatedAt,
                             long studentCount, MainTeacherDto mainTeacher,
                             List<StaffDto> staff, List<ScheduleDto> schedules) {
}
