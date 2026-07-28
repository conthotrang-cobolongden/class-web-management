package com.classmanagement.fees.dto;

import com.classmanagement.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

// Item danh sach lan dong tien (GET /api/fees/{id}/payments) — kem ten nguoi ghi nhan
public record PaymentDto(Long id, BigDecimal amount, String method, Instant paidAt,
                         String recordedByName, String note) {

    public static PaymentDto from(Payment p, String recordedByName) {
        return new PaymentDto(p.getId(), p.getAmount(), p.getMethod(), p.getPaidAt(),
                recordedByName, p.getNote());
    }
}
