package com.classmanagement.fees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

// POST /api/fees/{id}/payments (FR-6.4) — paid_at la ISO datetime (UTC)
public record CreatePaymentRequest(
        @NotNull(message = "Thiếu trường bắt buộc: amount")
        BigDecimal amount,

        @NotBlank(message = "Thiếu trường bắt buộc: method")
        String method,

        @NotNull(message = "Thiếu trường bắt buộc: paid_at")
        Instant paidAt,

        String note) {
}
