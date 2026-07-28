package com.classmanagement.grades;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.CsvUtil;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.Grade;
import com.classmanagement.domain.GradeColumn;
import com.classmanagement.domain.GradeColumnRepository;
import com.classmanagement.domain.GradeRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.grades.dto.CreateGradeColumnRequest;
import com.classmanagement.grades.dto.GradeColumnDto;
import com.classmanagement.grades.dto.GradeMatrixResponse;
import com.classmanagement.grades.dto.SaveGradesRequest;
import com.classmanagement.grades.dto.UpdateGradeColumnRequest;
import com.classmanagement.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Quan ly diem (FR-5): cot diem linh hoat, ma tran diem, bulk upsert, xuat CSV.
// Moi method check quyen theo lop qua ClassAccessService ngay dau (NFR-2).
@Service
public class GradeService {

    // Ket qua xuat CSV cho controller set header Content-Disposition
    public record CsvExport(String filename, byte[] content) {
    }

    private final GradeColumnRepository gradeColumns;
    private final GradeRepository grades;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final ClassAccessService classAccess;
    private final AuditService auditService;

    public GradeService(GradeColumnRepository gradeColumns, GradeRepository grades,
                        SchoolClassRepository classes, EnrollmentRepository enrollments,
                        StudentRepository students, ClassAccessService classAccess,
                        AuditService auditService) {
        this.gradeColumns = gradeColumns;
        this.grades = grades;
        this.classes = classes;
        this.enrollments = enrollments;
        this.students = students;
        this.classAccess = classAccess;
        this.auditService = auditService;
    }

    // ---------- Cot diem (FR-5.1) ----------

    public List<GradeColumnDto> listColumns(User me, Long classId) {
        classAccess.assertClassMember(me, classId);
        requireClass(classId);
        return gradeColumns.findByClassIdAndIsDeletedFalseOrderByIdAsc(classId).stream()
                .map(GradeColumnDto::from)
                .toList();
    }

    @Transactional
    public GradeColumnDto createColumn(User me, Long classId, CreateGradeColumnRequest req) {
        classAccess.assertClassManager(me, classId);
        requireClass(classId);

        BigDecimal weight = req.weight() == null ? BigDecimal.ONE : req.weight();
        BigDecimal maxScore = req.maxScore() == null ? BigDecimal.TEN : req.maxScore();
        assertPositive(weight, "weight");
        assertPositive(maxScore, "max_score");

        GradeColumn col = new GradeColumn();
        col.setClassId(classId);
        col.setName(req.name().trim());
        col.setWeight(weight);
        col.setMaxScore(maxScore);
        col.setColumnDate(req.columnDate());
        col.setCreatedBy(me.getId());
        gradeColumns.save(col);
        return GradeColumnDto.from(col);
    }

    @Transactional
    public GradeColumnDto updateColumn(User me, Long columnId, UpdateGradeColumnRequest req) {
        GradeColumn col = requireColumn(columnId);
        classAccess.assertClassManager(me, col.getClassId());

        Map<String, Object> oldValues = snapshot(col);
        boolean changed = false;

        if (req.has("name")) {
            String name = req.getString("name");
            if (name == null || name.isBlank()) {
                throw new ApiException(400, "Thiếu trường bắt buộc: name");
            }
            col.setName(name.trim());
            changed = true;
        }
        if (req.has("weight")) {
            BigDecimal weight = toDecimal(req.raw("weight"), "weight");
            assertPositive(weight, "weight");
            col.setWeight(weight);
            changed = true;
        }
        if (req.has("max_score")) {
            BigDecimal maxScore = toDecimal(req.raw("max_score"), "max_score");
            assertPositive(maxScore, "max_score");
            col.setMaxScore(maxScore);
            changed = true;
        }
        if (req.has("column_date")) {
            col.setColumnDate(toDate(req.raw("column_date")));
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }

        gradeColumns.save(col);
        // Sua cot diem la thao tac nhay cam (doi weight/max_score lam doi diem TB) -> audit (NFR-4)
        auditService.audit("grade_columns", col.getId(), "update", oldValues, snapshot(col), me.getId());
        return GradeColumnDto.from(col);
    }

    // Soft delete: diem da nhap giu nguyen de tra cuu (NFR-3)
    @Transactional
    public void deleteColumn(User me, Long columnId) {
        GradeColumn col = requireColumn(columnId);
        classAccess.assertClassManager(me, col.getClassId());

        Map<String, Object> oldValues = snapshot(col);
        col.setDeleted(true);
        gradeColumns.save(col);
        auditService.audit("grade_columns", col.getId(), "delete",
                oldValues, Map.of("is_deleted", true), me.getId());
    }

    // ---------- Ma tran diem (FR-5.2, FR-5.3) ----------

    public GradeMatrixResponse matrix(User me, Long classId) {
        classAccess.assertClassMember(me, classId);
        requireClass(classId);

        List<GradeColumn> columns = gradeColumns.findByClassIdAndIsDeletedFalseOrderByIdAsc(classId);
        List<Student> roster = currentStudents(classId);
        Map<Long, Map<Long, BigDecimal>> scoreByStudent = scoreMap(columns);

        List<GradeMatrixResponse.StudentRow> items = new ArrayList<>();
        for (Student s : roster) {
            Map<Long, BigDecimal> myScores =
                    scoreByStudent.getOrDefault(s.getId(), Map.of());
            Map<String, BigDecimal> scores = new LinkedHashMap<>();
            for (GradeColumn col : columns) {
                scores.put(String.valueOf(col.getId()), myScores.get(col.getId()));
            }
            items.add(new GradeMatrixResponse.StudentRow(
                    s.getId(), s.getFullName(), scores, weightedAverage(columns, myScores)));
        }
        return new GradeMatrixResponse(columns.stream().map(GradeColumnDto::from).toList(), items);
    }

    // ---------- Nhap diem hang loat (FR-5.2) ----------

    @Transactional
    public int saveGrades(User me, SaveGradesRequest req) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new ApiException(400, "Thiếu trường bắt buộc: items");
        }

        // Gom cot -> validate ton tai + tat ca cung 1 lop -> check member dung 1 lan
        Map<Long, GradeColumn> columnById = new HashMap<>();
        Long classId = null;
        for (SaveGradesRequest.Item item : req.items()) {
            if (item.gradeColumnId() == null) {
                throw new ApiException(400, "Thiếu trường bắt buộc: grade_column_id");
            }
            if (item.studentId() == null) {
                throw new ApiException(400, "Thiếu trường bắt buộc: student_id");
            }
            if (item.score() == null) {
                throw new ApiException(400, "Thiếu trường bắt buộc: score");
            }
            GradeColumn col = columnById.computeIfAbsent(item.gradeColumnId(), this::requireColumn);
            if (classId == null) {
                classId = col.getClassId();
            } else if (!classId.equals(col.getClassId())) {
                throw new ApiException(400, "Các cột điểm phải thuộc cùng một lớp");
            }
        }
        classAccess.assertClassMember(me, classId);

        // Nap 1 lan truoc vong lap (tranh 2 query SELECT cho moi o diem):
        // - roster HS dang hoc lop: vua validate ton tai, vua chan ghi diem cho HS
        //   ngoai lop (dong bo quy uoc voi AttendanceService.save)
        // - toan bo diem hien co cua cac cot lien quan -> upsert tren Map in-memory
        Set<Long> rosterIds = enrollments.findByClassIdAndLeftAtIsNull(classId).stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toSet());
        Map<String, Grade> existingByKey = grades.findByGradeColumnIdIn(columnById.keySet()).stream()
                .collect(Collectors.toMap(g -> g.getGradeColumnId() + ":" + g.getStudentId(), g -> g));

        int saved = 0;
        for (SaveGradesRequest.Item item : req.items()) {
            GradeColumn col = columnById.get(item.gradeColumnId());
            BigDecimal score = item.score();
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(col.getMaxScore()) > 0) {
                throw new ApiException(400, "Điểm phải trong khoảng 0.." + plain(col.getMaxScore()));
            }
            if (!rosterIds.contains(item.studentId())) {
                throw new ApiException(400, "Học sinh không thuộc lớp của cột điểm");
            }

            Grade existing = existingByKey.get(item.gradeColumnId() + ":" + item.studentId());
            if (existing == null) {
                Grade g = new Grade();
                g.setGradeColumnId(item.gradeColumnId());
                g.setStudentId(item.studentId());
                g.setScore(score);
                g.setEnteredBy(me.getId());
                grades.save(g);
                // Cung o diem xuat hien lan nua trong request -> di duong update, khong insert doi
                existingByKey.put(item.gradeColumnId() + ":" + item.studentId(), g);
            } else if (existing.getScore().compareTo(score) != 0) {
                // Sua diem da co -> audit gia tri cu/moi (NFR-4)
                Map<String, Object> oldValues = Map.of("score", existing.getScore());
                existing.setScore(score);
                existing.setUpdatedBy(me.getId());
                grades.save(existing);
                auditService.audit("grades", existing.getId(), "update",
                        oldValues, Map.of("score", score), me.getId());
            }
            // Diem gui len bang diem cu: khong ghi de (giu entered_by/updated_at), van tinh da luu
            saved++;
        }
        return saved;
    }

    // ---------- Xuat CSV (FR-5.4) ----------

    public CsvExport exportCsv(User me, Long classId) {
        classAccess.assertClassMember(me, classId);
        SchoolClass cls = requireClass(classId);

        List<GradeColumn> columns = gradeColumns.findByClassIdAndIsDeletedFalseOrderByIdAsc(classId);
        List<Student> roster = currentStudents(classId);
        Map<Long, Map<Long, BigDecimal>> scoreByStudent = scoreMap(columns);

        List<String[]> rows = new ArrayList<>();
        String[] header = new String[columns.size() + 2];
        header[0] = "Họ tên";
        for (int i = 0; i < columns.size(); i++) {
            header[i + 1] = columns.get(i).getName();
        }
        header[header.length - 1] = "Điểm TB";
        rows.add(header);

        for (Student s : roster) {
            Map<Long, BigDecimal> myScores = scoreByStudent.getOrDefault(s.getId(), Map.of());
            String[] row = new String[columns.size() + 2];
            row[0] = s.getFullName();
            for (int i = 0; i < columns.size(); i++) {
                BigDecimal score = myScores.get(columns.get(i).getId());
                row[i + 1] = score == null ? "" : plain(score);
            }
            BigDecimal avg = weightedAverage(columns, myScores);
            row[row.length - 1] = avg == null ? "" : avg.toPlainString();
            rows.add(row);
        }

        return new CsvExport("bang-diem-" + slugify(cls.getName()) + ".csv", CsvUtil.build(rows));
    }

    // ---------- Helpers ----------

    private SchoolClass requireClass(Long classId) {
        return classes.findById(classId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));
    }

    private GradeColumn requireColumn(Long columnId) {
        return gradeColumns.findByIdAndIsDeletedFalse(columnId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy cột điểm"));
    }

    // HS dang hoc lop (enrollment mo), xep theo ten cho ma tran + CSV
    private List<Student> currentStudents(Long classId) {
        List<Long> studentIds = enrollments.findByClassIdAndLeftAtIsNull(classId).stream()
                .map(Enrollment::getStudentId)
                .toList();
        return students.findAllById(studentIds).stream()
                .sorted(Comparator.comparing(Student::getFullName).thenComparing(Student::getId))
                .toList();
    }

    // Diem cua ca lop: studentId -> (columnId -> score)
    private Map<Long, Map<Long, BigDecimal>> scoreMap(List<GradeColumn> columns) {
        if (columns.isEmpty()) {
            return Map.of();
        }
        List<Long> columnIds = columns.stream().map(GradeColumn::getId).toList();
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        for (Grade g : grades.findByGradeColumnIdIn(columnIds)) {
            result.computeIfAbsent(g.getStudentId(), k -> new HashMap<>())
                    .put(g.getGradeColumnId(), g.getScore());
        }
        return result;
    }

    // FR-5.3: diem TB co trong so = SUM(score/max_score*10*weight) / SUM(weight)
    // tren cac cot HS CO diem; lam tron 2 so le; null neu chua co diem nao
    private BigDecimal weightedAverage(List<GradeColumn> columns, Map<Long, BigDecimal> myScores) {
        BigDecimal sumWeighted = BigDecimal.ZERO;
        BigDecimal sumWeight = BigDecimal.ZERO;
        for (GradeColumn col : columns) {
            BigDecimal score = myScores.get(col.getId());
            if (score == null) {
                continue;
            }
            sumWeighted = sumWeighted.add(score.multiply(BigDecimal.TEN).multiply(col.getWeight())
                    .divide(col.getMaxScore(), 10, RoundingMode.HALF_UP));
            sumWeight = sumWeight.add(col.getWeight());
        }
        if (sumWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return sumWeighted.divide(sumWeight, 2, RoundingMode.HALF_UP);
    }

    private static void assertPositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(400, "Trường " + field + " phải > 0");
        }
    }

    // Ep gia tri tho tu JSON (Number/String) ve BigDecimal cho update PATCH
    private static BigDecimal toDecimal(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Trường " + field + " phải là số");
        }
    }

    private static LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường column_date phải có dạng YYYY-MM-DD");
        }
    }

    // Snapshot cac truong nghiep vu cua cot diem cho audit (old/new values)
    private static Map<String, Object> snapshot(GradeColumn col) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", col.getName());
        values.put("weight", col.getWeight());
        values.put("max_score", col.getMaxScore());
        values.put("column_date", Objects.toString(col.getColumnDate(), null));
        return values;
    }

    // "10.00" -> "10", "8.50" -> "8.5" — hien so gon nhu ban Node (JS number)
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // Ten lop -> slug ASCII cho filename ("Toán 6A1" -> "toan-6a1"):
    // header HTTP khong nhan ky tu ngoai Latin-1 nen phai bo dau tieng Viet
    private static String slugify(String name) {
        String noAccent = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String slug = noAccent.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "lop" : slug;
    }
}
