package com.classmanagement.students.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Body POST /api/students/{id}/enroll — gan HS chua co lop vao lop (FR-3.2)
public record EnrollRequest(
        @NotNull(message = "Thiếu trường bắt buộc: class_id")
        Long classId,

        @NotNull(message = "Thiếu trường bắt buộc: joined_at")
        LocalDate joinedAt,

        BigDecimal customMonthlyFee) {
}
