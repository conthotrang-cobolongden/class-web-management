package com.classmanagement.sessions.dto;

import com.classmanagement.domain.ClassSession;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

// Item buoi hoc theo API.md muc 3 — snake_case do Jackson config toan cuc
public record SessionDto(Long id,
                         Long classId,
                         String className,
                         LocalDate sessionDate,
                         @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                         @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                         String status,
                         boolean isMakeup,
                         Long makeupForSessionId,
                         String cancelReason,
                         String note,
                         long attendanceCount) {

    public static SessionDto from(ClassSession s, String className, long attendanceCount) {
        return new SessionDto(s.getId(), s.getClassId(), className, s.getSessionDate(),
                s.getStartTime(), s.getEndTime(), s.getStatus(), s.isMakeup(),
                s.getMakeupForSessionId(), s.getCancelReason(), s.getNote(), attendanceCount);
    }
}
