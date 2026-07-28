package com.classmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

// Khoan phi thang cua tung HS (FR-6) — map 1:1 bang monthly_fees.
// So tien phai dong thuc te = COALESCE(adjusted_amount, base_amount) — xem FeeStatusService.
@Entity
@Table(name = "monthly_fees")
public class MonthlyFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "fee_year", nullable = false)
    private Integer feeYear;

    @Column(name = "fee_month", nullable = false)
    private Integer feeMonth;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal baseAmount;

    // NULL = khong dieu chinh (FR-6.3)
    @Column(name = "adjusted_amount", precision = 12, scale = 0)
    private BigDecimal adjustedAmount;

    @Column(name = "adjustment_reason", length = 300)
    private String adjustmentReason;

    @Column(name = "adjusted_by")
    private Long adjustedBy;

    // unpaid / partial / paid
    @Column(nullable = false, length = 20)
    private String status = "unpaid";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Integer getFeeYear() {
        return feeYear;
    }

    public void setFeeYear(Integer feeYear) {
        this.feeYear = feeYear;
    }

    public Integer getFeeMonth() {
        return feeMonth;
    }

    public void setFeeMonth(Integer feeMonth) {
        this.feeMonth = feeMonth;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }

    public void setAdjustedAmount(BigDecimal adjustedAmount) {
        this.adjustedAmount = adjustedAmount;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }

    public Long getAdjustedBy() {
        return adjustedBy;
    }

    public void setAdjustedBy(Long adjustedBy) {
        this.adjustedBy = adjustedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
