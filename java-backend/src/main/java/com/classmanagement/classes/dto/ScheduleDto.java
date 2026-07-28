package com.classmanagement.classes.dto;

import com.classmanagement.domain.ClassSchedule;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

// Khung gio tuan: {id, day_of_week (1 = Thu 2 ... 7 = Chu nhat), start_time, end_time}
// @JsonFormat HH:mm de dam bao dung dinh dang gio cua contract (API.md muc 0)
public record ScheduleDto(Long id, Integer dayOfWeek,
                          @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                          @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    public static ScheduleDto from(ClassSchedule s) {
        return new ScheduleDto(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime());
    }
}
