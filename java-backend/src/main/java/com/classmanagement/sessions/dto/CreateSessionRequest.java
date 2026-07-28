package com.classmanagement.sessions.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

// Body POST /api/sessions — tao buoi bu/buoi le (FR-2.4)
public record CreateSessionRequest(
        @NotNull(message = "Thiếu trường bắt buộc: class_id")
        Long classId,

        @NotNull(message = "Thiếu trường bắt buộc: session_date")
        LocalDate sessionDate,

        @NotNull(message = "Thiếu trường bắt buộc: start_time")
        LocalTime startTime,

        @NotNull(message = "Thiếu trường bắt buộc: end_time")
        LocalTime endTime,

        Boolean isMakeup,

        Long makeupForSessionId,

        String note) {
}
