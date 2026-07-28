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
import java.time.LocalDate;

// HS thuoc lop nao + lich su chuyen lop (FR-3.4) — map 1:1 bang enrollments.
// left_at NULL = dang hoc lop nay; chuyen lop = dong ban ghi cu + tao ban ghi moi.
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Column(name = "left_at")
    private LocalDate leftAt;

    // transferred / quit
    @Column(name = "leave_reason", length = 20)
    private String leaveReason;

    @Column(name = "transferred_to_class_id")
    private Long transferredToClassId;

    // NULL = theo phi lop (FR-6.1)
    @Column(name = "custom_monthly_fee", precision = 12, scale = 0)
    private BigDecimal customMonthlyFee;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDate joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDate getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDate leftAt) {
        this.leftAt = leftAt;
    }

    public String getLeaveReason() {
        return leaveReason;
    }

    public void setLeaveReason(String leaveReason) {
        this.leaveReason = leaveReason;
    }

    public Long getTransferredToClassId() {
        return transferredToClassId;
    }

    public void setTransferredToClassId(Long transferredToClassId) {
        this.transferredToClassId = transferredToClassId;
    }

    public BigDecimal getCustomMonthlyFee() {
        return customMonthlyFee;
    }

    public void setCustomMonthlyFee(BigDecimal customMonthlyFee) {
        this.customMonthlyFee = customMonthlyFee;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
