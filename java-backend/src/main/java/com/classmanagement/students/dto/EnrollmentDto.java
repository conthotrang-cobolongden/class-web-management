package com.classmanagement.students.dto;

import com.classmanagement.domain.Enrollment;

import java.math.BigDecimal;
import java.time.LocalDate;

// Mot dong lich su lop cua HS (GET /api/students/{id}) — left_at null = dang hoc
public record EnrollmentDto(Long id, Long classId, String className, LocalDate joinedAt,
                            LocalDate leftAt, String leaveReason, Long transferredToClassId,
                            BigDecimal customMonthlyFee) {

    public static EnrollmentDto from(Enrollment e, String className) {
        return new EnrollmentDto(e.getId(), e.getClassId(), className, e.getJoinedAt(),
                e.getLeftAt(), e.getLeaveReason(), e.getTransferredToClassId(),
                e.getCustomMonthlyFee());
    }
}
