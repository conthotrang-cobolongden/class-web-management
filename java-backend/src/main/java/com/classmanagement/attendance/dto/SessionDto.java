package com.classmanagement.attendance.dto;

import com.classmanagement.domain.ClassSession;

import java.time.LocalDate;
import java.time.LocalTime;

// Thong tin buoi hoc tra kem bang diem danh (GET /api/sessions/{id}/attendance)
public record SessionDto(Long id, Long classId, String className, LocalDate sessionDate,
                         LocalTime startTime, LocalTime endTime, String status, boolean isMakeup,
                         Long makeupForSessionId, String cancelReason, String note) {

    public static SessionDto from(ClassSession s, String className) {
        return new SessionDto(s.getId(), s.getClassId(), className, s.getSessionDate(),
                s.getStartTime(), s.getEndTime(), s.getStatus(), s.isMakeup(),
                s.getMakeupForSessionId(), s.getCancelReason(), s.getNote());
    }
}
