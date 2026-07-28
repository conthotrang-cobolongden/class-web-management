package com.classmanagement.students.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Body POST /api/students — neu co class_id thi tao luon enrollment (joined_at = enrollment_date)
public record CreateStudentRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: full_name")
        String fullName,

        LocalDate dob,
        String gender,
        String schoolName,
        String parentName,
        String parentPhone,

        @NotNull(message = "Thiếu trường bắt buộc: enrollment_date")
        LocalDate enrollmentDate,

        String status,
        String note,
        Long classId,
        BigDecimal customMonthlyFee) {
}
