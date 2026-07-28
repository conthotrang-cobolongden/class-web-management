package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMonthlyFeeIdOrderByPaidAtAscIdAsc(Long monthlyFeeId);

    // Tong da dong cua 1 khoan phi — dung tinh lai status (FR-6.4)
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.monthlyFeeId = :feeId
            """)
    BigDecimal sumByMonthlyFeeId(@Param("feeId") Long feeId);

    // Tong da dong theo tung khoan phi — [monthlyFeeId, sum] (danh sach fees, bao cao doanh thu)
    @Query("""
            select p.monthlyFeeId, coalesce(sum(p.amount), 0) from Payment p
            where p.monthlyFeeId in :feeIds
            group by p.monthlyFeeId
            """)
    List<Object[]> sumByMonthlyFeeIds(@Param("feeIds") Collection<Long> feeIds);
}
