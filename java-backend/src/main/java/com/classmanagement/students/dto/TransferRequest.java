package com.classmanagement.students.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Body POST /api/students/{id}/transfer (FR-3.4)
public record TransferRequest(
        @NotNull(message = "Thiếu trường bắt buộc: to_class_id")
        Long toClassId,

        @NotNull(message = "Thiếu trường bắt buộc: effective_date")
        LocalDate effectiveDate) {
}
