package com.classmanagement.grades.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

// Body POST /api/classes/{id}/grade-columns (FR-5.1)
// weight mac dinh 1, max_score mac dinh 10 (nhu default cua schema) — validate > 0 o service
public record CreateGradeColumnRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: name")
        String name,

        BigDecimal weight,

        BigDecimal maxScore,

        LocalDate columnDate) {
}
