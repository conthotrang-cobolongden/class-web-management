package com.classmanagement.fees.dto;

// 201 khi ghi nhan payment: tra payment vua tao + status khoan phi sau khi tinh lai (FR-6.4)
public record CreatePaymentResponse(PaymentDto payment, String feeStatus) {
}
