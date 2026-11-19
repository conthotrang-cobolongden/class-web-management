package com.classmanagement.common;

import com.classmanagement.TestUsers;
import com.classmanagement.domain.ClassSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceEditPolicyTest {

    private static final Instant START = Instant.parse("2026-07-28T11:00:00Z");
    private static final Instant END = Instant.parse("2026-07-28T13:00:00Z");

    @Test
    void assistantWindowIsInclusiveFromFifteenMinutesBeforeStartThroughFifteenMinutesAfterEnd() {
        AttendanceEditDecision opening = evaluate("2026-07-28T10:45:00Z", "assistant", "scheduled");
        AttendanceEditDecision closing = evaluate("2026-07-28T13:15:00Z", "assistant", "scheduled");

        assertTrue(opening.allowed());
        assertTrue(closing.allowed());
        assertEquals(START.minusSeconds(15 * 60), opening.opensAt());
        assertEquals(END.plusSeconds(15 * 60), opening.closesAt());
    }

    @Test
    void assistantCannotEditOutsideWindow() {
        assertFalse(evaluate("2026-07-28T10:44:59Z", "assistant", "scheduled").allowed());
        assertFalse(evaluate("2026-07-28T13:15:01Z", "assistant", "scheduled").allowed());
    }

    @Test
    void teacherKeepsExistingTwentyFourHourDeadline() {
        AttendanceEditDecision atDeadline = evaluate("2026-07-29T11:00:00Z", "teacher", "scheduled");
        AttendanceEditDecision afterDeadline = evaluate("2026-07-29T11:00:01Z", "teacher", "scheduled");

        assertTrue(atDeadline.allowed());
        assertFalse(afterDeadline.allowed());
        assertNull(atDeadline.opensAt());
        assertEquals(START.plusSeconds(24 * 60 * 60), atDeadline.closesAt());
    }

    @Test
    void adminIsAlwaysAllowedForScheduledSession() {
        AttendanceEditDecision decision = evaluate("2027-01-01T00:00:00Z", "admin", "scheduled");

        assertTrue(decision.allowed());
        assertNull(decision.opensAt());
        assertNull(decision.closesAt());
    }

    @Test
    void cancelledSessionIsLockedForEveryRole() {
        AttendanceEditDecision decision = evaluate("2026-07-28T12:00:00Z", "admin", "cancelled");

        assertFalse(decision.allowed());
        assertEquals("Buổi học đã hủy", decision.reason());
    }

    private static AttendanceEditDecision evaluate(String now, String role, String status) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        AttendanceEditPolicy policy = new AttendanceEditPolicy(clock);
        return policy.evaluate(switch (role) {
            case "admin" -> TestUsers.admin(1L);
            case "assistant" -> TestUsers.assistant(2L);
            default -> TestUsers.teacher(3L);
        }, session(status));
    }

    private static ClassSession session(String status) {
        ClassSession session = new ClassSession();
        session.setSessionDate(LocalDate.of(2026, 7, 28));
        session.setStartTime(LocalTime.of(18, 0));
        session.setEndTime(LocalTime.of(20, 0));
        session.setStatus(status);
        return session;
    }
}
