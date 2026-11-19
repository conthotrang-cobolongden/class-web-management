package com.classmanagement.common;

import com.classmanagement.domain.ClassSession;
import com.classmanagement.user.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class AttendanceEditPolicy {

    private static final Duration ASSISTANT_MARGIN = Duration.ofMinutes(15);
    private static final Duration TEACHER_WINDOW = Duration.ofHours(24);

    private final Clock clock;

    public AttendanceEditPolicy(Clock clock) {
        this.clock = clock;
    }

    public AttendanceEditDecision evaluate(User user, ClassSession session) {
        if ("cancelled".equals(session.getStatus())) {
            return denied(null, null, "Buổi học đã hủy");
        }

        String role = user.getRole();
        if ("admin".equals(role)) {
            return allowed(null, null);
        }

        Instant now = clock.instant();
        Instant startsAt = TimeUtil.sessionStartUtc(session.getSessionDate(), session.getStartTime());
        if ("assistant".equals(role)) {
            Instant opensAt = startsAt.minus(ASSISTANT_MARGIN);
            Instant closesAt = TimeUtil.sessionEndUtc(
                    session.getSessionDate(), session.getEndTime()).plus(ASSISTANT_MARGIN);
            if (now.isBefore(opensAt)) {
                return denied(opensAt, closesAt, "Chưa đến thời gian điểm danh của trợ giảng");
            }
            if (now.isAfter(closesAt)) {
                return denied(opensAt, closesAt, "Đã hết thời gian điểm danh của trợ giảng");
            }
            return allowed(opensAt, closesAt);
        }

        if ("teacher".equals(role)) {
            Instant closesAt = startsAt.plus(TEACHER_WINDOW);
            return now.isAfter(closesAt)
                    ? denied(null, closesAt, "Đã quá 24 giờ kể từ giờ bắt đầu buổi học")
                    : allowed(null, closesAt);
        }

        return denied(null, null, "Bạn không có quyền sửa điểm danh");
    }

    private static AttendanceEditDecision allowed(Instant opensAt, Instant closesAt) {
        return new AttendanceEditDecision(true, opensAt, closesAt, null);
    }

    private static AttendanceEditDecision denied(Instant opensAt, Instant closesAt, String reason) {
        return new AttendanceEditDecision(false, opensAt, closesAt, reason);
    }
}
