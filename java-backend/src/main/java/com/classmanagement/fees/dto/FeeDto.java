package com.classmanagement.fees.dto;

import com.classmanagement.domain.MonthlyFee;

import java.math.BigDecimal;

// Item danh sach khoan phi (GET /api/fees) — kem ten HS/lop + so phai dong + tong da dong.
// amount_due = COALESCE(adjusted_amount, base_amount) — cung cong thuc voi FeeStatusService.
public record FeeDto(Long id, Long studentId, String studentName, Long classId, String className,
                     Integer feeYear, Integer feeMonth, BigDecimal baseAmount, BigDecimal adjustedAmount,
                     String adjustmentReason, String status, BigDecimal amountDue, BigDecimal paidTotal) {

    public static FeeDto from(MonthlyFee f, String studentName, String className, BigDecimal paidTotal) {
        BigDecimal amountDue = f.getAdjustedAmount() != null ? f.getAdjustedAmount() : f.getBaseAmount();
        return new FeeDto(f.getId(), f.getStudentId(), studentName, f.getClassId(), className,
                f.getFeeYear(), f.getFeeMonth(), f.getBaseAmount(), f.getAdjustedAmount(),
                f.getAdjustmentReason(), f.getStatus(), amountDue, paidTotal);
    }
}
