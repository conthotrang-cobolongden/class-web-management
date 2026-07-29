package com.classmanagement.classes;

import com.classmanagement.classes.dto.ClassDetailDto;
import com.classmanagement.classes.dto.ClassDto;
import com.classmanagement.classes.dto.ClassStudentDto;
import com.classmanagement.classes.dto.CreateClassRequest;
import com.classmanagement.classes.dto.MainTeacherDto;
import com.classmanagement.classes.dto.ReplaceStaffRequest;
import com.classmanagement.classes.dto.ScheduleDto;
import com.classmanagement.classes.dto.ScheduleItemRequest;
import com.classmanagement.classes.dto.StaffDto;
import com.classmanagement.classes.dto.UpdateClassRequest;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.ClassSchedule;
import com.classmanagement.domain.ClassScheduleRepository;
import com.classmanagement.domain.ClassSession;
import com.classmanagement.domain.ClassSessionRepository;
import com.classmanagement.domain.ClassStaff;
import com.classmanagement.domain.ClassStaffRepository;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Module lop hoc (FR-2): CRUD lop, phan cong nhan su, khung gio tuan,
// sinh buoi hoc tu khung tuan, danh sach HS dang hoc.
@Service
public class ClassService {

    public record GenerateResult(int created, int skipped) {
    }

    public static final List<Integer> GRADES = List.of(6, 7, 8, 9);
    public static final List<String> STATUSES = List.of("active", "closed");

    private static final Pattern SCHOOL_YEAR = Pattern.compile("^\\d{4}-\\d{4}$");

    private final SchoolClassRepository classes;
    private final ClassStaffRepository classStaff;
    private final ClassScheduleRepository schedules;
    private final ClassSessionRepository sessions;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AuditService audit;
    private final UserRepository users;
    private final ClassAccessService access;

    public ClassService(SchoolClassRepository classes, ClassStaffRepository classStaff,
                        ClassScheduleRepository schedules, ClassSessionRepository sessions,
                        EnrollmentRepository enrollments, StudentRepository students,
                        UserRepository users, ClassAccessService access, AuditService audit) {
        this.classes = classes;
        this.classStaff = classStaff;
        this.schedules = schedules;
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.audit = audit;
        this.students = students;
        this.users = users;
        this.access = access;
    }

    // ---------- GET /api/classes: admin thay tat ca, GV/TG chi lop minh ----------
    public PageResponse<ClassDto> list(User me, Pagination p, Integer grade, String status, String schoolYear) {
        List<Long> visibleIds = access.visibleClassIds(me);
        // GV/TG chua duoc gan lop nao -> danh sach rong (khong duoc query "in ()" tren SQL Server)
        if (visibleIds != null && visibleIds.isEmpty()) {
            return new PageResponse<>(List.of(), 0, p.page(), p.pageSize());
        }

        Pageable pageable = PageRequest.of(p.page() - 1, p.pageSize(), Sort.by("id"));
        String statusFilter = blankToNull(status);
        String yearFilter = blankToNull(schoolYear);
        Page<SchoolClass> result = (visibleIds == null)
                ? classes.search(grade, statusFilter, yearFilter, pageable)
                : classes.searchWithin(visibleIds, grade, statusFilter, yearFilter, pageable);

        List<Long> ids = result.getContent().stream().map(SchoolClass::getId).toList();
        Map<Long, Long> counts = studentCounts(ids);
        Map<Long, MainTeacherDto> mains = mainTeachers(ids);

        List<ClassDto> items = result.getContent().stream()
                .map(c -> ClassDto.from(c, counts.getOrDefault(c.getId(), 0L), mains.get(c.getId())))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), p.page(), p.pageSize());
    }

    // ---------- POST /api/classes (admin) ----------
    @Transactional
    public ClassDetailDto create(User me, CreateClassRequest req) {
        assertAdmin(me);
        assertGrade(req.grade());
        assertSchoolYear(req.schoolYear());
        assertDates(req.startDate(), req.endDate());
        assertFee(req.defaultMonthlyFee());
        if (classes.existsByNameAndSchoolYear(req.name().trim(), req.schoolYear())) {
            throw new ApiException(409, "Tên lớp đã tồn tại trong năm học này");
        }

        SchoolClass c = new SchoolClass();
        c.setName(req.name().trim());
        c.setGrade(req.grade());
        c.setSchoolYear(req.schoolYear());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setDefaultMonthlyFee(req.defaultMonthlyFee());
        c.setNote(emptyToNull(req.note()));
        classes.save(c);
        audit.audit("classes", "classes", c.getId(), "create",
                "Tạo lớp " + c.getName(), null, classSnapshot(c), me.getId());
        return detail(c);
    }

    // ---------- GET /api/classes/{id} (member) ----------
    public ClassDetailDto get(User me, Long id) {
        access.assertClassMember(me, id);
        return detail(find(id));
    }

    // ---------- PUT /api/classes/{id} (admin, partial nhu PUT users) ----------
    @Transactional
    public ClassDetailDto update(User me, Long id, UpdateClassRequest req) {
        assertAdmin(me);
        SchoolClass c = find(id);
        Map<String, Object> oldValues = classSnapshot(c);

        boolean changed = false;
        if (req.has("name")) {
            String name = req.getString("name");
            if (name == null || name.isBlank()) {
                throw new ApiException(400, "Trường name không được để trống");
            }
            c.setName(name.trim());
            changed = true;
        }
        if (req.has("grade")) {
            Integer grade = req.getInt("grade");
            assertGrade(grade);
            c.setGrade(grade);
            changed = true;
        }
        if (req.has("school_year")) {
            String schoolYear = req.getString("school_year");
            assertSchoolYear(schoolYear);
            c.setSchoolYear(schoolYear);
            changed = true;
        }
        if (req.has("start_date")) {
            c.setStartDate(requireDate(req, "start_date"));
            changed = true;
        }
        if (req.has("end_date")) {
            c.setEndDate(requireDate(req, "end_date"));
            changed = true;
        }
        if (req.has("default_monthly_fee")) {
            BigDecimal fee = req.getMoney("default_monthly_fee");
            assertFee(fee);
            c.setDefaultMonthlyFee(fee);
            changed = true;
        }
        if (req.has("note")) {
            c.setNote(emptyToNull(req.getString("note")));
            changed = true;
        }
        if (req.has("status")) {
            String status = req.getString("status");
            if (status == null || !STATUSES.contains(status)) {
                throw new ApiException(400, "Trường status phải là một trong: " + String.join(", ", STATUSES));
            }
            c.setStatus(status);
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }
        // Check chan tren gia tri SAU khi ghep partial (vd chi doi start_date van phai < end_date)
        assertDates(c.getStartDate(), c.getEndDate());
        if (classes.existsByNameAndSchoolYearAndIdNot(c.getName(), c.getSchoolYear(), c.getId())) {
            throw new ApiException(409, "Tên lớp đã tồn tại trong năm học này");
        }
        classes.save(c);
        audit.audit("classes", "classes", c.getId(), "update",
                "Cập nhật lớp " + c.getName(), oldValues, classSnapshot(c), me.getId());
        return detail(c);
    }

    // ---------- PUT /api/classes/{id}/staff (admin): thay toan bo phan cong ----------
    @Transactional
    public List<StaffDto> replaceStaff(User me, Long classId, ReplaceStaffRequest req) {
        assertAdmin(me);
        find(classId);
        List<Map<String, Object>> oldStaff = classStaff.findByClassId(classId).stream()
                .map(s -> Map.<String, Object>of("user_id", s.getUserId(), "staff_role", s.getStaffRole()))
                .toList();

        List<Long> assistantIds = req.assistantIds() == null ? List.of() : req.assistantIds();
        List<Long> allIds = new ArrayList<>();
        allIds.add(req.mainTeacherId());
        for (Long userId : assistantIds) {
            if (userId == null) {
                throw new ApiException(400, "Trường assistant_ids chứa phần tử không hợp lệ");
            }
            allIds.add(userId);
        }
        if (new HashSet<>(allIds).size() != allIds.size()) {
            throw new ApiException(400, "Danh sách phân công bị trùng người");
        }

        Map<Long, User> byId = new HashMap<>();
        for (Long userId : allIds) {
            User u = users.findById(userId)
                    .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));
            if (!u.isActive()) {
                throw new ApiException(400, "Tài khoản " + u.getFullName() + " đã bị vô hiệu hóa");
            }
            byId.put(userId, u);
        }

        // Xoa het roi ghi lai; flush de DELETE chay truoc INSERT (unique class_id + user_id)
        classStaff.deleteByClassId(classId);
        classStaff.flush();

        List<StaffDto> result = new ArrayList<>();
        result.add(insertStaff(classId, req.mainTeacherId(), "main", byId));
        for (Long userId : assistantIds) {
            result.add(insertStaff(classId, userId, "assistant", byId));
        }
        audit.audit("classes", "class_staff", classId, "update",
                "Cập nhật nhân sự lớp #" + classId, oldStaff, result, me.getId());
        return result;
    }

    // ---------- PUT /api/classes/{id}/schedules (manager): thay toan bo khung tuan ----------
    @Transactional
    public List<ScheduleDto> replaceSchedules(User me, Long classId, List<ScheduleItemRequest> items) {
        access.assertClassManager(me, classId);
        find(classId);
        List<ScheduleDto> oldSchedules = schedules.findByClassIdOrderByDayOfWeekAscStartTimeAsc(classId)
                .stream().map(ScheduleDto::from).toList();

        List<ClassSchedule> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScheduleItemRequest item : (items == null ? List.<ScheduleItemRequest>of() : items)) {
            if (item == null || item.dayOfWeek() == null || item.dayOfWeek() < 1 || item.dayOfWeek() > 7) {
                throw new ApiException(400, "Trường day_of_week phải từ 1 (Thứ 2) đến 7 (Chủ nhật)");
            }
            LocalTime start = parseTime(item.startTime(), "start_time");
            LocalTime end = parseTime(item.endTime(), "end_time");
            if (!end.isAfter(start)) {
                throw new ApiException(400, "Giờ kết thúc phải sau giờ bắt đầu");
            }
            if (!seen.add(item.dayOfWeek() + "#" + start)) {
                throw new ApiException(400, "Khung giờ bị trùng (thứ + giờ bắt đầu)");
            }
            ClassSchedule s = new ClassSchedule();
            s.setClassId(classId);
            s.setDayOfWeek(item.dayOfWeek());
            s.setStartTime(start);
            s.setEndTime(end);
            rows.add(s);
        }

        // Xoa het roi ghi lai; flush de DELETE chay truoc INSERT (unique class_id + day + start)
        schedules.deleteByClassId(classId);
        schedules.flush();
        schedules.saveAll(rows);
        audit.audit("sessions", "class_schedules", classId, "update",
                "Cập nhật thời khóa biểu lớp #" + classId, oldSchedules,
                rows.stream().map(ScheduleDto::from).toList(), me.getId());

        rows.sort(Comparator.comparing(ClassSchedule::getDayOfWeek).thenComparing(ClassSchedule::getStartTime));
        return rows.stream().map(ScheduleDto::from).toList();
    }

    // ---------- POST /api/classes/{id}/generate-sessions (manager, FR-2.3) ----------
    // Sinh buoi tu khung tuan trong [start_date, end_date]; idempotent nho unique (class, date, start)
    @Transactional
    public GenerateResult generateSessions(User me, Long classId) {
        access.assertClassManager(me, classId);
        SchoolClass c = find(classId);

        Map<Integer, List<ClassSchedule>> byDayOfWeek =
                schedules.findByClassIdOrderByDayOfWeekAscStartTimeAsc(classId).stream()
                        .collect(Collectors.groupingBy(ClassSchedule::getDayOfWeek));
        int created = 0;
        int skipped = 0;
        for (LocalDate d = c.getStartDate(); !d.isAfter(c.getEndDate()); d = d.plusDays(1)) {
            // DayOfWeek.getValue(): 1 = Thu 2 ... 7 = Chu nhat — trung quy uoc day_of_week trong DB
            for (ClassSchedule s : byDayOfWeek.getOrDefault(d.getDayOfWeek().getValue(), List.of())) {
                if (sessions.existsByClassIdAndSessionDateAndStartTime(classId, d, s.getStartTime())) {
                    skipped++;
                    continue;
                }
                ClassSession session = new ClassSession();
                session.setClassId(classId);
                session.setSessionDate(d);
                session.setStartTime(s.getStartTime());
                session.setEndTime(s.getEndTime());
                sessions.save(session);
                created++;
            }
        }
        if (created > 0) {
            audit.audit("sessions", "sessions", classId, "create",
                    "Sinh buổi học cho lớp " + c.getName(), null,
                    Map.of("created", created, "skipped", skipped), me.getId());
        }
        return new GenerateResult(created, skipped);
    }


    private static Map<String, Object> classSnapshot(SchoolClass value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", value.getName());
        snapshot.put("grade", value.getGrade());
        snapshot.put("school_year", value.getSchoolYear());
        snapshot.put("start_date", value.getStartDate());
        snapshot.put("end_date", value.getEndDate());
        snapshot.put("default_monthly_fee", value.getDefaultMonthlyFee());
        snapshot.put("status", value.getStatus());
        snapshot.put("note", value.getNote());
        return snapshot;
    }
    // ---------- GET /api/classes/{id}/students (member): HS co enrollment dang mo ----------
    public List<ClassStudentDto> listStudents(User me, Long classId) {
        access.assertClassMember(me, classId);
        find(classId);

        List<Enrollment> open = enrollments.findByClassIdAndLeftAtIsNull(classId);
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> byId = students
                .findAllById(open.stream().map(Enrollment::getStudentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return open.stream()
                .map(e -> {
                    Student s = byId.get(e.getStudentId());
                    return new ClassStudentDto(s.getId(), s.getStudentCode(), s.getFullName(), s.getDob(), s.getGender(),
                            s.getParentPhone(), s.getStatus(), e.getJoinedAt(), e.getCustomMonthlyFee());
                })
                .sorted(Comparator.comparing(ClassStudentDto::fullName)
                        .thenComparing(ClassStudentDto::studentId))
                .toList();
    }

    // ---------- helpers ----------

    private SchoolClass find(Long id) {
        return classes.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));
    }

    private ClassDetailDto detail(SchoolClass c) {
        List<ClassStaff> rows = classStaff.findByClassId(c.getId());
        Map<Long, String> names = userNames(rows.stream().map(ClassStaff::getUserId).toList());
        List<StaffDto> staffDtos = rows.stream()
                .sorted(Comparator.comparing((ClassStaff s) -> "main".equals(s.getStaffRole()) ? 0 : 1)
                        .thenComparing(ClassStaff::getId))
                .map(s -> new StaffDto(s.getUserId(), names.get(s.getUserId()), s.getStaffRole()))
                .toList();
        MainTeacherDto main = rows.stream()
                .filter(s -> "main".equals(s.getStaffRole()))
                .findFirst()
                .map(s -> new MainTeacherDto(s.getUserId(), names.get(s.getUserId())))
                .orElse(null);
        List<ScheduleDto> scheduleDtos = schedules.findByClassIdOrderByDayOfWeekAscStartTimeAsc(c.getId())
                .stream().map(ScheduleDto::from).toList();
        long studentCount = enrollments.countByClassIdAndLeftAtIsNull(c.getId());
        return new ClassDetailDto(c.getId(), c.getName(), c.getGrade(), c.getSchoolYear(),
                c.getStartDate(), c.getEndDate(), c.getDefaultMonthlyFee(), c.getStatus(), c.getNote(),
                c.getCreatedAt(), c.getUpdatedAt(), studentCount, main, staffDtos, scheduleDtos);
    }

    private StaffDto insertStaff(Long classId, Long userId, String staffRole, Map<Long, User> byId) {
        ClassStaff s = new ClassStaff();
        s.setClassId(classId);
        s.setUserId(userId);
        s.setStaffRole(staffRole);
        classStaff.save(s);
        return new StaffDto(userId, byId.get(userId).getFullName(), staffRole);
    }

    private Map<Long, Long> studentCounts(Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : enrollments.countOpenByClassIds(classIds)) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private Map<Long, MainTeacherDto> mainTeachers(Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        List<ClassStaff> mains = classStaff.findByClassIdIn(classIds).stream()
                .filter(s -> "main".equals(s.getStaffRole()))
                .toList();
        Map<Long, String> names = userNames(mains.stream().map(ClassStaff::getUserId).toList());
        Map<Long, MainTeacherDto> map = new HashMap<>();
        for (ClassStaff s : mains) {
            map.put(s.getClassId(), new MainTeacherDto(s.getUserId(), names.get(s.getUserId())));
        }
        return map;
    }

    private Map<Long, String> userNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }

    private static void assertAdmin(User user) {
        if (!ClassAccessService.isAdmin(user)) {
            throw new ApiException(403, "Bạn không có quyền thực hiện thao tác này");
        }
    }

    private static void assertGrade(Integer grade) {
        if (grade == null || !GRADES.contains(grade)) {
            throw new ApiException(400, "Trường grade phải là một trong: 6, 7, 8, 9");
        }
    }

    private static void assertSchoolYear(String schoolYear) {
        if (schoolYear == null || !SCHOOL_YEAR.matcher(schoolYear).matches()) {
            throw new ApiException(400, "Trường school_year phải có dạng YYYY-YYYY (vd: 2026-2027)");
        }
    }

    private static void assertDates(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new ApiException(400, "Ngày kết thúc phải sau ngày bắt đầu");
        }
    }

    private static void assertFee(BigDecimal fee) {
        if (fee == null) {
            throw new ApiException(400, "Trường default_monthly_fee phải là số");
        }
        if (fee.signum() < 0) {
            throw new ApiException(400, "Trường default_monthly_fee không được âm");
        }
    }

    private static LocalDate requireDate(UpdateClassRequest req, String field) {
        LocalDate date = req.getDate(field);
        if (date == null) {
            throw new ApiException(400, "Trường " + field + " không hợp lệ (định dạng YYYY-MM-DD)");
        }
        return date;
    }

    private static LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Thiếu trường bắt buộc: " + field);
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " không hợp lệ (định dạng HH:mm)");
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
