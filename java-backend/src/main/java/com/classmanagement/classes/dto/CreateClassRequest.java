package com.classmanagement.classes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Body POST /api/classes: {name, grade(6-9), school_year("2026-2027"), start_date, end_date, default_monthly_fee, note?}
// Gia tri grade/school_year/ngay/phi duoc validate sau trong ClassService (message tieng Viet)
public record CreateClassRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: name")
        String name,

        @NotNull(message = "Thiếu trường bắt buộc: grade")
        Integer grade,

        @NotBlank(message = "Thiếu trường bắt buộc: school_year")
        String schoolYear,

        @NotNull(message = "Thiếu trường bắt buộc: start_date")
        LocalDate startDate,

        @NotNull(message = "Thiếu trường bắt buộc: end_date")
        LocalDate endDate,

        @NotNull(message = "Thiếu trường bắt buộc: default_monthly_fee")
        BigDecimal defaultMonthlyFee,

        String note) {
}
