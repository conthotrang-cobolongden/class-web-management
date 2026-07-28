package com.classmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

// Tung lan dong tien, cho phep thieu/thua nhieu lan (FR-6.4) — map 1:1 bang payments
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monthly_fee_id", nullable = false)
    private Long monthlyFeeId;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    // cash / transfer
    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMonthlyFeeId() {
        return monthlyFeeId;
    }

    public void setMonthlyFeeId(Long monthlyFeeId) {
        this.monthlyFeeId = monthlyFeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
