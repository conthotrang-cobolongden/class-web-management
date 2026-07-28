package com.classmanagement.common;

import com.classmanagement.domain.MonthlyFee;
import com.classmanagement.domain.MonthlyFeeRepository;
import com.classmanagement.domain.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Tinh so tien phai dong + dong bo status khoan phi (FR-6.4) — port tu backend/src/services/fees.js
@Service
public class FeeStatusService {

    private final MonthlyFeeRepository monthlyFees;
    private final PaymentRepository payments;

    public FeeStatusService(MonthlyFeeRepository monthlyFees, PaymentRepository payments) {
        this.monthlyFees = monthlyFees;
        this.payments = payments;
    }

    // So tien phai dong thuc te = COALESCE(adjusted_amount, base_amount)
    public BigDecimal amountDue(MonthlyFee fee) {
        return fee.getAdjustedAmount() != null ? fee.getAdjustedAmount() : fee.getBaseAmount();
    }

    // Tong da dong cua 1 khoan phi (0 neu chua co payment nao)
    public BigDecimal paidTotal(Long feeId) {
        BigDecimal sum = payments.sumByMonthlyFeeId(feeId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    // paid >= due -> "paid"; paid > 0 -> "partial"; con lai -> "unpaid"
    public static String statusFor(BigDecimal due, BigDecimal paid) {
        if (paid.compareTo(due) >= 0) {
            return "paid";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return "partial";
        }
        return "unpaid";
    }

    // Tinh lai status theo tong da dong, luu neu doi. Goi sau khi them payment / dieu chinh phi.
    @Transactional
    public String refreshFeeStatus(MonthlyFee fee) {
        String status = statusFor(amountDue(fee), paidTotal(fee.getId()));
        if (!status.equals(fee.getStatus())) {
            fee.setStatus(status);
            monthlyFees.save(fee);
        }
        return status;
    }

    @Transactional
    public String refreshFeeStatus(Long feeId) {
        MonthlyFee fee = monthlyFees.findById(feeId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy khoản phí"));
        return refreshFeeStatus(fee);
    }
}
