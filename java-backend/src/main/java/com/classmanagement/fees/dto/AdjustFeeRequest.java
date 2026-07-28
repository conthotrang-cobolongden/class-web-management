package com.classmanagement.fees.dto;

import java.math.BigDecimal;

// PUT /api/fees/{id}/adjust (FR-6.3) — reason bat buoc, check o service de tra dung message
public record AdjustFeeRequest(BigDecimal adjustedAmount, String adjustmentReason) {
}
