package com.classmanagement.attendance;

import com.classmanagement.attendance.dto.AttendanceItemRequest;
import com.classmanagement.attendance.dto.AttendanceRosterItem;
import com.classmanagement.attendance.dto.AttendanceRosterResponse;
import com.classmanagement.attendance.dto.AttendanceStatsItem;
import com.classmanagement.attendance.dto.SaveAttendanceRequest;
import com.classmanagement.attendance.dto.SessionDto;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AttendanceEditDecision;
import com.classmanagement.common.AttendanceEditPolicy;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.TimeUtil;
import com.classmanagement.domain.AttendanceRecord;
import com.classmanagement.domain.AttendanceRecordRepository;
import com.classmanagement.domain.ClassSession;
import com.classmanagement.domain.ClassSessionRepository;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Diem danh theo buoi hoc (FR-4) — API.md muc 4: roster buoi hoc, bulk upsert co khoa 24h, thong ke chuyen can
@Service
public class AttendanceService {

    public static final List<String> STATUSES = List.of("present", "absent", "late", "excused");

    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository attendance;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final UserRepository users;
    private final ClassAccessService classAccess;
    private final AuditService auditService;
    private final AttendanceEditPolicy editPolicy;

    public AttendanceService(ClassSessionRepository sessions, AttendanceRecordRepository attendance,
                             EnrollmentRepository enrollments, StudentRepository students,
                             SchoolClassRepository classes, UserRepository users, ClassAccessService classAccess,
                             AuditService auditService, AttendanceEditPolicy editPolicy) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.enrollments = enrollments;
        this.students = students;
        this.classes = classes;
        this.users = users;
        this.classAccess = classAccess;
        this.editPolicy = editPolicy;
        this.auditService = auditService;
    }

    // GET /api/sessions/{id}/attendance — moi HS co enrollment mo tai ngay buoi hoc,
    // merge voi ban ghi diem danh neu co (chua diem danh -> status/note/recorded_* = null)
    public AttendanceRosterResponse roster(User me, Long sessionId) {
        ClassSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy buổi học"));
        classAccess.assertClassMember(me, session.getClassId());

        String className = classes.findById(session.getClassId())
                .map(SchoolClass::getName)
                .orElse(null);

        List<Enrollment> open = enrollments.findOpenByClassAtDate(session.getClassId(), session.getSessionDate());
        List<Student> roster = students.findAllById(open.stream().map(Enrollment::getStudentId).toList());

        Map<Long, AttendanceRecord> recordByStudent = attendance.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, r -> r));

        Map<Long, String> recorderNames = fullNamesByIds(recordByStudent.values().stream()
                .map(AttendanceRecord::getRecordedBy)
                .toList());

        List<AttendanceRosterItem> items = roster.stream()
                .sorted(byVietnameseName())
                .map(st -> {
                    AttendanceRecord r = recordByStudent.get(st.getId());
                    return new AttendanceRosterItem(st.getId(), st.getFullName(),
                            r == null ? null : r.getStatus(),
                            r == null ? null : r.getNote(),
                            r == null ? null : recorderNames.get(r.getRecordedBy()),
                            r == null ? null : r.getRecordedAt());
                })
                .toList();
        AttendanceEditDecision decision = editPolicy.evaluate(me, session);
        return new AttendanceRosterResponse(SessionDto.from(session, className), items, decision.allowed(),
                decision.opensAt(), decision.closesAt(), decision.reason());
    }

    // PUT /api/sessions/{id}/attendance — bulk upsert (FR-4.1, FR-4.2).
    // Tra ve so ban ghi da xu ly (saved) de controller ghep message.
    @Transactional
    public int save(User me, Long sessionId, SaveAttendanceRequest req) {
        ClassSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy buổi học"));
        classAccess.assertClassMember(me, session.getClassId());

        if ("cancelled".equals(session.getStatus())) {
            throw new ApiException(400, "Không thể điểm danh buổi học đã hủy");
        }

        List<AttendanceItemRequest> items = req == null ? null : req.items();
        if (items == null || items.isEmpty()) {
            throw new ApiException(400, "Danh sách điểm danh (items) không được rỗng");
        }

        // Backend la nguon quyet dinh cua so sua theo vai tro.
        AttendanceEditDecision decision = editPolicy.evaluate(me, session);
        if (!decision.allowed()) {
            throw new ApiException(403, decision.reason());
        }

        Set<Long> rosterIds = enrollments.findOpenByClassAtDate(session.getClassId(), session.getSessionDate())
                .stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toSet());

        // Validate toan bo truoc khi ghi de khong luu nua chung
        for (AttendanceItemRequest item : items) {
            if (item == null || item.studentId() == null) {
                throw new ApiException(400, "Thiếu student_id trong danh sách điểm danh");
            }
            if (item.status() == null || !STATUSES.contains(item.status())) {
                throw new ApiException(400, "Trường status phải là một trong: " + String.join(", ", STATUSES));
            }
            if (item.note() != null && item.note().length() > 300) {
                throw new ApiException(400, "Ghi chú tối đa 300 ký tự");
            }
            if (!rosterIds.contains(item.studentId())) {
                throw new ApiException(400, "Học sinh không thuộc lớp tại ngày diễn ra buổi học");
            }
        }

        Map<Long, AttendanceRecord> existing = attendance.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, r -> r));

        for (AttendanceItemRequest item : items) {
            String note = emptyToNull(item.note());
            AttendanceRecord record = existing.get(item.studentId());
            if (record == null) {
                record = new AttendanceRecord();
                record.setSessionId(sessionId);
                record.setStudentId(item.studentId());
                record.setStatus(item.status());
                record.setNote(note);
                record.setRecordedBy(me.getId());
                attendance.save(record);
                existing.put(item.studentId(), record);
                auditService.audit("attendance", record.getId(), "create",
                        null, snapshot(record.getStatus(), record.getNote()), me.getId());
            } else {
                boolean changed = !item.status().equals(record.getStatus())
                        || !Objects.equals(note, record.getNote());
                if (!changed) {
                    continue; // giu nguyen recorded_by/updated_at khi khong co gi thay doi
                }
                Map<String, Object> old = snapshot(record.getStatus(), record.getNote());
                record.setStatus(item.status());
                record.setNote(note);
                record.setUpdatedBy(me.getId());
                attendance.save(record);
                auditService.audit("attendance", record.getId(), "update",
                        old, snapshot(record.getStatus(), record.getNote()), me.getId());
            }
        }
        return items.size();
    }

    // GET /api/attendance/stats?class_id&from&to — FR-4.4: thong ke tren cac buoi scheduled
    // co ngay <= hom nay (gio VN) trong khoang [from, to]; HS = dang hoc lop (enrollment mo)
    public List<AttendanceStatsItem> stats(User me, Long classId, String fromParam, String toParam) {
        if (classId == null) {
            throw new ApiException(400, "Thiếu tham số class_id");
        }
        classAccess.assertClassMember(me, classId);
        if (!classes.existsById(classId)) {
            throw new ApiException(404, "Không tìm thấy lớp học");
        }

        LocalDate from = parseDate(fromParam, "from");
        LocalDate to = parseDate(toParam, "to");
        LocalDate today = LocalDate.now(TimeUtil.VN_ZONE);
        LocalDate effectiveTo = (to == null || to.isAfter(today)) ? today : to;
        LocalDate effectiveFrom = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        // Chi tinh buoi DA QUA theo GIO bat dau (khong phai theo ngay) — buoi toi nay
        // chua dien ra khong duoc keo tut ty le; dong bo cach loc voi ReportService
        Instant now = Instant.now();
        List<ClassSession> pastSessions = effectiveFrom.isAfter(effectiveTo)
                ? List.of()
                : sessions.findByClassIdAndStatusAndSessionDateBetween(classId, "scheduled",
                        effectiveFrom, effectiveTo).stream()
                        .filter(s -> !TimeUtil.sessionStartUtc(s.getSessionDate(), s.getStartTime()).isAfter(now))
                        .toList();
        long totalSessions = pastSessions.size();

        // [studentId, status, count] -> map de tra cuu tung HS
        Map<Long, Map<String, Long>> countByStudent = new HashMap<>();
        if (!pastSessions.isEmpty()) {
            List<Long> sessionIds = pastSessions.stream().map(ClassSession::getId).toList();
            for (Object[] row : attendance.countByStudentAndStatus(sessionIds)) {
                countByStudent.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                        .put((String) row[1], (Long) row[2]);
            }
        }

        List<Enrollment> open = enrollments.findByClassIdAndLeftAtIsNull(classId);
        List<Student> roster = students.findAllById(open.stream().map(Enrollment::getStudentId).toList());

        return roster.stream()
                .sorted(byVietnameseName())
                .map(st -> {
                    Map<String, Long> counts = countByStudent.getOrDefault(st.getId(), Map.of());
                    long present = counts.getOrDefault("present", 0L);
                    long absent = counts.getOrDefault("absent", 0L);
                    long late = counts.getOrDefault("late", 0L);
                    long excused = counts.getOrDefault("excused", 0L);
                    Double rate = totalSessions == 0 ? null
                            : BigDecimal.valueOf((present + late) * 100.0 / totalSessions)
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .doubleValue();
                    return new AttendanceStatsItem(st.getId(), st.getFullName(),
                            present, absent, late, excused, totalSessions, rate);
                })
                .toList();
    }

    private Map<Long, String> fullNamesByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private static Comparator<Student> byVietnameseName() {
        Collator vi = Collator.getInstance(Locale.of("vi", "VN"));
        return Comparator.comparing(Student::getFullName, vi).thenComparing(Student::getId);
    }

    private static Map<String, Object> snapshot(String status, String note) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status);
        values.put("note", note);
        return values;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " phải có định dạng YYYY-MM-DD");
        }
    }
}
