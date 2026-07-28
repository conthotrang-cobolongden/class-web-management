package com.classmanagement.classes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// HS dang hoc lop (GET /api/classes/{id}/students):
// {student_id, full_name, dob, gender, parent_phone, status, joined_at, custom_monthly_fee}
public record ClassStudentDto(Long studentId, String studentCode, String fullName, LocalDate dob, String gender,
                              String parentPhone, String status, LocalDate joinedAt,
                              BigDecimal customMonthlyFee) {
}
