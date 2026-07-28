package com.classmanagement.sessions.dto;

import com.classmanagement.domain.ClassSession;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record WeeklySessionDto(Long id,
                               Long classId,
                               String className,
                               LocalDate sessionDate,
                               @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                               String status,
                               boolean isMakeup,
                               String cancelReason,
                               String note,
                               long attendanceCount) {

    public static WeeklySessionDto from(ClassSession session, String className, long attendanceCount) {
        return new WeeklySessionDto(session.getId(), session.getClassId(), className,
                session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                session.getStatus(), session.isMakeup(), session.getCancelReason(),
                session.getNote(), attendanceCount);
    }
}
