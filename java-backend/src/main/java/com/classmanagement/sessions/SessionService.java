package com.classmanagement.sessions;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.AttendanceRecordRepository;
import com.classmanagement.domain.ClassSession;
import com.classmanagement.domain.ClassSessionRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.sessions.dto.CancelSessionRequest;
import com.classmanagement.sessions.dto.CreateSessionRequest;
import com.classmanagement.sessions.dto.SessionDto;
import com.classmanagement.sessions.dto.WeeklySessionDto;
import com.classmanagement.sessions.dto.UpdateSessionRequest;
import com.classmanagement.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Sessions — API.md muc 3 (FR-2.3, FR-2.4): list theo lop, tao buoi bu/le, sua buoi scheduled, huy co ly do
@Service
public class SessionService {

    private final ClassSessionRepository sessions;
    private final SchoolClassRepository classes;
    private final AttendanceRecordRepository attendance;
    private final ClassAccessService access;
    private final AuditService audit;

    public SessionService(ClassSessionRepository sessions, SchoolClassRepository classes,
                          AttendanceRecordRepository attendance, ClassAccessService access,
                          AuditService audit) {
        this.sessions = sessions;
        this.classes = classes;
        this.attendance = attendance;
        this.access = access;
        this.audit = audit;
    }

    // GET /api/sessions — admin: class_id tuy chon; GV/TG: bat buoc class_id + phai la member
    public PageResponse<SessionDto> list(User me, Long classId, String fromParam, String toParam,
                                         String status, Pagination p) {
        if (!ClassAccessService.isAdmin(me) && classId == null) {
            throw new ApiException(400, "Thiếu trường bắt buộc: class_id");
        }
        if (classId != null) {
            access.assertClassMember(me, classId);
        }
        LocalDate from = parseDateParam(fromParam, "from");
        LocalDate to = parseDateParam(toParam, "to");

        Pageable pageable = PageRequest.of(p.page() - 1, p.pageSize(),
                Sort.by("sessionDate", "startTime", "id"));
        Page<ClassSession> result = sessions.search(classId, from, to, emptyToNull(status), pageable);

        List<ClassSession> content = result.getContent();
        Map<Long, String> classNames = classNamesOf(content);
        Map<Long, Long> counts = attendanceCountsOf(content);

        List<SessionDto> items = content.stream()
                .map(s -> SessionDto.from(s, classNames.get(s.getClassId()),
                        counts.getOrDefault(s.getId(), 0L)))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), p.page(), p.pageSize());
    }

    public List<WeeklySessionDto> week(User me, String fromParam, String toParam) {
        LocalDate from = parseDate(fromParam, "from");
        LocalDate to = parseDate(toParam, "to");
        if (from.getDayOfWeek() != DayOfWeek.MONDAY
                || !to.equals(from.plusDays(6))) {
            throw new ApiException(400, "Khoảng lịch tuần phải từ thứ Hai đến Chủ nhật (đúng 7 ngày)");
        }

        List<Long> visibleClassIds = access.visibleClassIds(me);
        List<ClassSession> content;
        if (visibleClassIds == null) {
            content = sessions.findBySessionDateBetween(from, to);
        } else if (visibleClassIds.isEmpty()) {
            return List.of();
        } else {
            content = sessions.findByClassIdInAndSessionDateBetween(visibleClassIds, from, to);
        }

        Map<Long, String> classNames = classNamesOf(content);
        Map<Long, Long> counts = attendanceCountsOf(content);

        return content.stream()
                .sorted(Comparator.comparing(ClassSession::getSessionDate)
                        .thenComparing(ClassSession::getStartTime)
                        .thenComparing(ClassSession::getClassId)
                        .thenComparing(ClassSession::getId))
                .map(session -> WeeklySessionDto.from(session, classNames.get(session.getClassId()),
                        counts.getOrDefault(session.getId(), 0L)))
                .toList();
    }

    // POST /api/sessions — tao buoi bu/buoi le (FR-2.4), unique (class, date, start_time) -> 409
    @Transactional
    public SessionDto create(User me, CreateSessionRequest req) {
        access.assertClassManager(me, req.classId());
        SchoolClass clazz = classes.findById(req.classId())
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));

        if (!req.endTime().isAfter(req.startTime())) {
            throw new ApiException(400, "Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (req.makeupForSessionId() != null) {
            ClassSession original = sessions.findById(req.makeupForSessionId())
                    .orElseThrow(() -> new ApiException(404, "Không tìm thấy buổi học được bù"));
            if (!original.getClassId().equals(req.classId())) {
                throw new ApiException(400, "Buổi bù phải cùng lớp với buổi bị hủy");
            }
        }
        if (sessions.existsByClassIdAndSessionDateAndStartTime(req.classId(), req.sessionDate(),
                req.startTime())) {
            throw new ApiException(409, "Lớp đã có buổi học vào ngày giờ này");
        }

        ClassSession s = new ClassSession();
        s.setClassId(req.classId());
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setMakeup(Boolean.TRUE.equals(req.isMakeup()));
        s.setMakeupForSessionId(req.makeupForSessionId());
        s.setNote(emptyToNull(req.note()));
        sessions.save(s);
        audit.audit("sessions", "sessions", s.getId(), "create",
                "Tạo buổi học lớp " + clazz.getName(), null, snapshot(s), me.getId());
        return SessionDto.from(s, clazz.getName(), 0);
    }

    // PUT /api/sessions/{id} — partial: session_date, start_time, end_time, note (chi buoi scheduled)
    @Transactional
    public SessionDto update(User me, Long id, UpdateSessionRequest req) {
        ClassSession s = sessions.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy buổi học"));
        access.assertClassManager(me, s.getClassId());
        if (!"scheduled".equals(s.getStatus())) {
            throw new ApiException(400, "Không thể sửa buổi học đã bị hủy");
        }
        Map<String, Object> oldValues = snapshot(s);

        LocalDate oldDate = s.getSessionDate();
        LocalTime oldStart = s.getStartTime();

        boolean changed = false;
        if (req.has("session_date")) {
            s.setSessionDate(parseDate(req.get("session_date"), "session_date"));
            changed = true;
        }
        if (req.has("start_time")) {
            s.setStartTime(parseTime(req.get("start_time"), "start_time"));
            changed = true;
        }
        if (req.has("end_time")) {
            s.setEndTime(parseTime(req.get("end_time"), "end_time"));
            changed = true;
        }
        if (req.has("note")) {
            s.setNote(emptyToNull(req.get("note")));
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }
        if (!s.getEndTime().isAfter(s.getStartTime())) {
            throw new ApiException(400, "Giờ kết thúc phải sau giờ bắt đầu");
        }
        // Doi ngay/gio -> check trung lich (ban ghi hien tai van giu (oldDate, oldStart) trong DB
        // nen moi ban ghi match cap moi chac chan la buoi khac)
        boolean slotChanged = !Objects.equals(oldDate, s.getSessionDate())
                || !Objects.equals(oldStart, s.getStartTime());
        if (slotChanged && sessions.existsByClassIdAndSessionDateAndStartTime(s.getClassId(),
                s.getSessionDate(), s.getStartTime())) {
            throw new ApiException(409, "Lớp đã có buổi học vào ngày giờ này");
        }
        sessions.save(s);
        audit.audit("sessions", "sessions", s.getId(), "update",
                "Cập nhật buổi học #" + s.getId(), oldValues, snapshot(s), me.getId());

        String className = classes.findById(s.getClassId()).map(SchoolClass::getName).orElse(null);
        return SessionDto.from(s, className, attendance.countBySessionId(s.getId()));
    }

    // POST /api/sessions/{id}/cancel — status=cancelled + audit (FR-2.4, NFR-4)
    @Transactional
    public void cancel(User me, Long id, CancelSessionRequest req) {
        ClassSession s = sessions.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy buổi học"));
        access.assertClassManager(me, s.getClassId());
        if ("cancelled".equals(s.getStatus())) {
            throw new ApiException(400, "Buổi học đã bị hủy trước đó");
        }

        Map<String, Object> oldValues = Map.of("status", s.getStatus());
        s.setStatus("cancelled");
        s.setCancelReason(req.cancelReason().trim());
        sessions.save(s);

        Map<String, Object> newValues = Map.of("status", "cancelled",
                "cancel_reason", s.getCancelReason());
        // action phai thuoc CHECK ck_audit_logs_action (create/update/delete) — "cancel" bi DB tu choi
        audit.audit("sessions", "sessions", s.getId(), "update",
                "Hủy buổi học #" + s.getId(), oldValues, newValues, me.getId());
    }

    // ---- helpers ----

    private static Map<String, Object> snapshot(ClassSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("class_id", session.getClassId());
        values.put("session_date", session.getSessionDate());
        values.put("start_time", session.getStartTime());
        values.put("end_time", session.getEndTime());
        values.put("status", session.getStatus());
        values.put("is_makeup", session.isMakeup());
        values.put("makeup_for_session_id", session.getMakeupForSessionId());
        values.put("note", session.getNote());
        values.put("cancel_reason", session.getCancelReason());
        return values;
    }

    private Map<Long, String> classNamesOf(List<ClassSession> content) {
        Set<Long> classIds = content.stream().map(ClassSession::getClassId)
                .collect(Collectors.toSet());
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classes.findAllById(classIds).stream()
                .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));
    }

    private Map<Long, Long> attendanceCountsOf(List<ClassSession> content) {
        List<Long> sessionIds = content.stream().map(ClassSession::getId).toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : attendance.countBySessionIds(sessionIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    // Query param ngay: vang mat/rong -> null (khong loc)
    private static LocalDate parseDateParam(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDate(value.trim(), field);
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null) {
            throw new ApiException(400, "Trường " + field + " không đúng định dạng YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " không đúng định dạng YYYY-MM-DD");
        }
    }

    private static LocalTime parseTime(String value, String field) {
        if (value == null) {
            throw new ApiException(400, "Trường " + field + " không đúng định dạng HH:mm");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " không đúng định dạng HH:mm");
        }
    }
}
