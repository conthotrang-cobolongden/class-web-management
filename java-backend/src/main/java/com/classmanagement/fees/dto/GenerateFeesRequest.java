package com.classmanagement.fees.dto;

import jakarta.validation.constraints.NotNull;

// POST /api/fees/generate — sinh khoan phi thang cho toan trung tam (FR-6.2, chi admin)
public record GenerateFeesRequest(
        @NotNull(message = "Thiếu trường bắt buộc: year")
        Integer year,

        @NotNull(message = "Thiếu trường bắt buộc: month")
        Integer month) {
}
