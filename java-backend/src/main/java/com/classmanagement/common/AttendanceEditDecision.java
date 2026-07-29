package com.classmanagement.common;

import java.time.Instant;

public record AttendanceEditDecision(boolean allowed, Instant opensAt, Instant closesAt, String reason) {
}
