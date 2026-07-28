package com.classmanagement.students;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.CreateStudentRequest;
import com.classmanagement.students.dto.EnrollRequest;
import com.classmanagement.students.dto.EnrollmentDto;
import com.classmanagement.students.dto.StudentDetailDto;
import com.classmanagement.students.dto.StudentDto;
import com.classmanagement.students.dto.TransferRequest;
import com.classmanagement.students.dto.UpdateStudentRequest;
import com.classmanagement.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Quan ly ho so hoc sinh + enrollment (FR-3) — quyen theo lop check qua ClassAccessService (NFR-2)
@Service
public class StudentService {
    public record TransferClassOption(Long id, String name, String schoolYear) {
    }

    public static final List<String> STATUSES = List.of("active", "paused", "left");
    public static final List<String> GENDERS = List.of("male", "female", "other");

    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final StudentEnrollmentRepository openEnrollments;
    private final SchoolClassRepository classes;
    private final ClassAccessService access;
    private final AuditService auditService;

    public StudentService(StudentRepository students,
                          EnrollmentRepository enrollments,
                          StudentEnrollmentRepository openEnrollments,
                          SchoolClassRepository classes,
                          ClassAccessService access,
                          AuditService auditService) {
        this.students = students;
        this.enrollments = enrollments;
        this.openEnrollments = openEnrollments;
        this.classes = classes;
        this.access = access;
        this.auditService = auditService;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static void assertStatus(String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new ApiException(400, "Trường status phải là một trong: " + String.join(", ", STATUSES));
        }
    }

    // gender nullable trong DB — chi validate khi co gia tri
    private static void assertGender(String gender) {
        if (gender != null && !GENDERS.contains(gender)) {
            throw new ApiException(400, "Trường gender phải là một trong: " + String.join(", ", GENDERS));
        }
    }

    private static void assertFeeNotNegative(BigDecimal fee) {
        if (fee != null && fee.signum() < 0) {
            throw new ApiException(400, "Trường custom_monthly_fee phải là số không âm");
        }
    }

    private Student findStudent(Long id) {
        return students.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy học sinh"));
    }

    private SchoolClass findClass(Long id) {
        return classes.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));
    }

    // Quyen sua ho so / chuyen lop / phi rieng: admin HOAC GV chinh lop HS dang hoc.
    // Khong phai admin ma HS chua co lop -> khong the la GV chinh cua lop nao -> 403.
    private void assertManagerOfCurrentClass(User me, Enrollment current) {
        if (ClassAccessService.isAdmin(me)) {
            return;
        }
        if (current == null) {
            throw new ApiException(403, ClassAccessService.MANAGER_DENIED);
        }
        access.assertClassManager(me, current.getClassId());
    }

    // Chuyen lop: Admin hoac GV/TG duoc phan cong vao lop nguon.
    private void assertStaffOfCurrentClass(User me, Enrollment current) {
        if (ClassAccessService.isAdmin(me)) {
            return;
        }
        if (current == null) {
            throw new ApiException(403, ClassAccessService.STAFF_DENIED);
        }
        access.assertClassStaffOrAdmin(me, current.getClassId());
    }

    // GET /api/students — admin: tat ca; GV/TG: chi HS dang hoc cac lop minh (FR-3.3)
    public PageResponse<StudentDto> list(User me, Pagination p, String q, Long classId,
                                         Integer grade, String status) {
        Pageable pageable = PageRequest.of(p.page() - 1, p.pageSize(), Sort.by("id"));
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String statusFilter = emptyToNull(status);

        List<Long> visible = access.visibleClassIds(me);
        Page<Student> result;
        if (visible == null) {
            result = students.search(query, statusFilter, classId, grade, pageable);
        } else if (visible.isEmpty()) {
            // GV/TG chua duoc gan lop nao -> khong thay HS nao
            return new PageResponse<>(List.of(), 0, p.page(), p.pageSize());
        } else {
            result = students.searchWithin(visible, query, statusFilter, classId, grade, pageable);
        }

        Map<Long, SchoolClass> currentClassByStudent =
                currentClassOf(result.getContent().stream().map(Student::getId).toList());
        List<StudentDto> items = result.getContent().stream()
                .map(s -> StudentDto.from(s, currentClassByStudent.get(s.getId())))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), p.page(), p.pageSize());
    }

    // Map studentId -> lop dang hoc (enrollment mo) cho ca trang bang 2 query batch
    private Map<Long, SchoolClass> currentClassOf(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<Enrollment> open = openEnrollments.findByStudentIdInAndLeftAtIsNull(studentIds);
        if (open.isEmpty()) {
            return Map.of();
        }
        Map<Long, SchoolClass> classById = new HashMap<>();
        classes.findAllById(open.stream().map(Enrollment::getClassId).distinct().toList())
                .forEach(c -> classById.put(c.getId(), c));
        Map<Long, SchoolClass> result = new HashMap<>();
        for (Enrollment e : open) {
            result.put(e.getStudentId(), classById.get(e.getClassId()));
        }
        return result;
    }

    // POST /api/students — admin (class_id tuy chon) hoac GV chinh (bat buoc kem class_id lop minh)
    @Transactional
    public StudentDto create(User me, CreateStudentRequest req) {
        if (!ClassAccessService.isAdmin(me)) {
            if (req.classId() == null) {
                throw new ApiException(403, ClassAccessService.MANAGER_DENIED);
            }
            access.assertClassManager(me, req.classId());
        }
        if (req.status() != null) {
            assertStatus(req.status());
        }
        assertGender(emptyToNull(req.gender()));
        assertFeeNotNegative(req.customMonthlyFee());

        SchoolClass target = req.classId() == null ? null : findClass(req.classId());

        Student s = new Student();
        s.setFullName(req.fullName());
        s.setDob(req.dob());
        s.setGender(emptyToNull(req.gender()));
        s.setSchoolName(emptyToNull(req.schoolName()));
        s.setParentName(emptyToNull(req.parentName()));
        s.setParentPhone(emptyToNull(req.parentPhone()));
        s.setEnrollmentDate(req.enrollmentDate());
        if (req.status() != null) {
            s.setStatus(req.status());
        }
        s.setNote(emptyToNull(req.note()));
        students.save(s);
        s.setStudentCode(StudentCodes.fromId(s.getId()));
        students.save(s);

        if (target != null) {
            Enrollment e = new Enrollment();
            e.setStudentId(s.getId());
            e.setClassId(target.getId());
            e.setJoinedAt(req.enrollmentDate());
            e.setCustomMonthlyFee(req.customMonthlyFee());
            e.setCreatedBy(me.getId());
            enrollments.save(e);
        }
        return StudentDto.from(s, target);
    }

    // GET /api/students/{id} — admin hoac staff cua lop HS dang/tung hoc
    public StudentDetailDto detail(User me, Long id) {
        List<Long> visible = access.visibleClassIds(me);
        if (visible != null
                && (visible.isEmpty() || !enrollments.existsByStudentIdAndClassIdIn(id, visible))) {
            throw new ApiException(403, ClassAccessService.MEMBER_DENIED);
        }
        Student s = findStudent(id);

        List<Enrollment> history = enrollments.findByStudentIdOrderByJoinedAtAscIdAsc(id);
        Map<Long, SchoolClass> classById = new HashMap<>();
        if (!history.isEmpty()) {
            classes.findAllById(history.stream().map(Enrollment::getClassId).distinct().toList())
                    .forEach(c -> classById.put(c.getId(), c));
        }
        SchoolClass current = history.stream()
                .filter(e -> e.getLeftAt() == null)
                .map(e -> classById.get(e.getClassId()))
                .findFirst()
                .orElse(null);
        List<EnrollmentDto> enrollmentDtos = history.stream()
                .map(e -> {
                    SchoolClass c = classById.get(e.getClassId());
                    return EnrollmentDto.from(e, c == null ? null : c.getName());
                })
                .toList();
        return StudentDetailDto.from(s, current, enrollmentDtos);
    }

    // PUT /api/students/{id} — partial ho so, khong doi lop o day (FR-3.1)
    @Transactional
    public StudentDto update(User me, Long id, UpdateStudentRequest req) {
        Enrollment current = enrollments.findByStudentIdAndLeftAtIsNull(id).orElse(null);
        assertManagerOfCurrentClass(me, current);
        Student s = findStudent(id);

        boolean changed = false;
        if (req.has("full_name")) {
            String fullName = req.get("full_name");
            if (fullName == null || fullName.isBlank()) {
                throw new ApiException(400, "Thiếu trường bắt buộc: full_name");
            }
            s.setFullName(fullName);
            changed = true;
        }
        if (req.has("dob")) {
            s.setDob(req.getDate("dob"));
            changed = true;
        }
        if (req.has("gender")) {
            String gender = emptyToNull(req.get("gender"));
            assertGender(gender);
            s.setGender(gender);
            changed = true;
        }
        if (req.has("school_name")) {
            s.setSchoolName(emptyToNull(req.get("school_name")));
            changed = true;
        }
        if (req.has("parent_name")) {
            s.setParentName(emptyToNull(req.get("parent_name")));
            changed = true;
        }
        if (req.has("parent_phone")) {
            s.setParentPhone(emptyToNull(req.get("parent_phone")));
            changed = true;
        }
        if (req.has("enrollment_date")) {
            LocalDate date = req.getDate("enrollment_date");
            if (date == null) {
                throw new ApiException(400, "Thiếu trường bắt buộc: enrollment_date");
            }
            s.setEnrollmentDate(date);
            changed = true;
        }
        if (req.has("status")) {
            String status = req.get("status");
            assertStatus(status);
            s.setStatus(status);
            changed = true;
        }
        if (req.has("note")) {
            s.setNote(emptyToNull(req.get("note")));
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }
        students.save(s);

        SchoolClass currentClass = current == null
                ? null
                : classes.findById(current.getClassId()).orElse(null);
        return StudentDto.from(s, currentClass);
    }

    public List<TransferClassOption> transferOptions(User me, Long id) {
        findStudent(id);
        Enrollment current = enrollments.findByStudentIdAndLeftAtIsNull(id).orElse(null);
        assertStaffOfCurrentClass(me, current);
        if (current == null) {
            throw new ApiException(400, "Học sinh chưa có lớp đang học");
        }
        Long sourceClassId = current.getClassId();
        return classes.findByStatus("active").stream()
                .filter(value -> !sourceClassId.equals(value.getId()))
                .sorted(Comparator.comparing(SchoolClass::getName).thenComparing(SchoolClass::getId))
                .map(value -> new TransferClassOption(
                        value.getId(), value.getName(), value.getSchoolYear()))
                .toList();
    }

    // POST /api/students/{id}/transfer — dong enrollment cu + mo enrollment moi (FR-3.4)
    @Transactional
    public void transfer(User me, Long id, TransferRequest req) {
        Enrollment current = enrollments.findByStudentIdAndLeftAtIsNull(id).orElse(null);
        assertStaffOfCurrentClass(me, current);
        findStudent(id);
        if (current == null) {
            throw new ApiException(400, "Học sinh chưa có lớp đang học");
        }
        SchoolClass target = findClass(req.toClassId());
        if (target.getId().equals(current.getClassId())) {
            throw new ApiException(400, "Lớp đích phải khác lớp hiện tại");
        }
        if (!"active".equals(target.getStatus())) {
            throw new ApiException(400, "Lớp đích phải đang hoạt động");
        }
        // Tranh vi pham ck_enrollments_left (left_at >= joined_at) -> 500
        if (req.effectiveDate().isBefore(current.getJoinedAt())) {
            throw new ApiException(400, "Ngày hiệu lực phải từ ngày vào lớp hiện tại trở đi");
        }
        Long fromClassId = current.getClassId();

        // Dong enrollment cu — du lieu diem/diem danh/phi lop cu giu nguyen (NFR-3)
        current.setLeftAt(req.effectiveDate());
        current.setLeaveReason("transferred");
        current.setTransferredToClassId(target.getId());
        // saveAndFlush bat buoc: UPDATE dong enrollment cu phai cham DB TRUOC khi INSERT
        // enrollment moi (IDENTITY insert chay ngay), khong thi dinh unique index
        // ux_enrollments_active (student_id WHERE left_at IS NULL) -> transfer fail 100%
        enrollments.saveAndFlush(current);

        // Mo enrollment moi — phi rieng KHONG mang sang lop moi (FR-5.5 tuong tu cho phi)
        Enrollment next = new Enrollment();
        next.setStudentId(id);
        next.setClassId(target.getId());
        next.setJoinedAt(req.effectiveDate());
        next.setCreatedBy(me.getId());
        enrollments.save(next);

        // NFR-4: audit chuyen lop — ai, luc nao, tu lop -> den lop
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("student_id", id);
        oldValues.put("class_id", fromClassId);
        oldValues.put("left_at", null);
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("left_at", req.effectiveDate().toString());
        newValues.put("leave_reason", "transferred");
        newValues.put("transferred_to_class_id", target.getId());
        newValues.put("new_enrollment_id", next.getId());
        auditService.audit("enrollments", current.getId(), "update", oldValues, newValues, me.getId());
    }

    // POST /api/students/{id}/enroll — gan HS chua co lop vao lop (admin hoac GV chinh lop dich)
    @Transactional
    public void enroll(User me, Long id, EnrollRequest req) {
        access.assertClassManager(me, req.classId());
        assertFeeNotNegative(req.customMonthlyFee());
        findClass(req.classId());
        findStudent(id);
        if (enrollments.existsByStudentIdAndLeftAtIsNull(id)) {
            throw new ApiException(400, "Học sinh đang có lớp, hãy dùng chức năng chuyển lớp");
        }
        Enrollment e = new Enrollment();
        e.setStudentId(id);
        e.setClassId(req.classId());
        e.setJoinedAt(req.joinedAt());
        e.setCustomMonthlyFee(req.customMonthlyFee());
        e.setCreatedBy(me.getId());
        enrollments.save(e);
    }

    // PUT /api/students/{id}/custom-fee — phi rieng tren enrollment dang mo (FR-6.1)
    @Transactional
    public void updateCustomFee(User me, Long id, Map<String, Object> body) {
        Enrollment current = enrollments.findByStudentIdAndLeftAtIsNull(id).orElse(null);
        assertManagerOfCurrentClass(me, current);
        findStudent(id);
        if (current == null) {
            throw new ApiException(400, "Học sinh chưa có lớp đang học");
        }
        if (!body.containsKey("custom_monthly_fee")) {
            throw new ApiException(400, "Thiếu trường bắt buộc: custom_monthly_fee");
        }
        current.setCustomMonthlyFee(parseFee(body.get("custom_monthly_fee")));
        enrollments.save(current);
    }

    // null = quay ve phi mac dinh cua lop
    private static BigDecimal parseFee(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal fee;
        try {
            fee = new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Trường custom_monthly_fee phải là số");
        }
        assertFeeNotNegative(fee);
        return fee;
    }
}
