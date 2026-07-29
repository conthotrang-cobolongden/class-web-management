package com.classmanagement.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

// NFR-7: gio nhap tay (session_date + start_time) hieu la gio VN -> quy ve UTC de so sanh.
// Phuc vu khoa diem danh 24h (FR-4.2) — tuong duong logic ben Node (utils/serialize.js).
public final class TimeUtil {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private TimeUtil() {
    }

    // Thoi diem bat dau buoi hoc (gio VN) tinh ra Instant UTC
    public static Instant sessionStartUtc(LocalDate sessionDate, LocalTime startTime) {
        return sessionDate.atTime(startTime).atZone(VN_ZONE).toInstant();
    }

    // Thoi diem ket thuc buoi hoc (gio VN) tinh ra Instant UTC.
    public static Instant sessionEndUtc(LocalDate sessionDate, LocalTime endTime) {
        return sessionDate.atTime(endTime).atZone(VN_ZONE).toInstant();
    }
}
