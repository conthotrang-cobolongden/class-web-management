package com.classmanagement.reports;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.FeeStatusService;
import com.classmanagement.common.TimeUtil;
import com.classmanagement.domain.AttendanceRecordRepository;
import com.classmanagement.domain.ClassSession;
import com.classmanagement.domain.ClassStaff;
import com.classmanagement.domain.ClassStaffRepository;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.MonthlyFee;
import com.classmanagement.domain.MonthlyFeeRepository;
import com.classmanagement.domain.PaymentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.reports.dto.AttendanceReportResponse;
import com.classmanagement.reports.dto.AttendanceStatsItem;
import com.classmanagement.reports.dto.DashboardResponse;
import com.classmanagement.reports.dto.RevenueReportResponse;
import com.classmanagement.user.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Bao cao & dashboard (FR-7). Chi doc du lieu, khong ghi — quyen chan ngay dau moi method (NFR-2).
@Service
public class ReportService {

    public static final String ADMIN_DENIED = "Chỉ Admin được thực hiện thao tác này";
    public static final int DEFAULT_ABSENT_THRESHOLD = 3;

    private final ClassAccessService access;
    private final FeeStatusService feeStatus;
    private final SchoolClassRepository classes;
    private final ClassStaffRepository classStaff;
    private final ReportSessionRepository reportSessions;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final MonthlyFeeRepository monthlyFees;
    private final PaymentRepository payments;

    public ReportService(ClassAccessService access, FeeStatusService feeStatus,
                         SchoolClassRepository classes, ClassStaffRepository classStaff,
                         ReportSessionRepository reportSessions, EnrollmentRepository enrollments,
                         StudentRepository students, AttendanceRecordRepository attendance,
                         MonthlyFeeRepository monthlyFees, PaymentRepository payments) {
        this.access = access;
        this.feeStatus = feeStatus;
        this.classes = classes;
        this.classStaff = classStaff;
        this.reportSessions = reportSessions;
        this.enrollments = enrollments;
        this.students = students;
        this.attendance = attendance;
        this.monthlyFees = monthlyFees;
        this.payments = payments;
    }

    // ===== FR-7.1: bao cao chuyen can theo lop =====

    public AttendanceReportResponse attendanceReport(User me, Long classId,
                                                     String fromParam, String toParam, String thresholdParam) {
        if (classId == null) {
            throw new ApiException(400, "Phải truyền class_id");
        }
        access.assertClassMember(me, classId);
        classes.findById(classId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));

        LocalDate from = parseDate("from", fromParam);
        LocalDate to = parseDate("to", toParam);
        int threshold = parseThreshold(thresholdParam);

        // Chi tinh cac buoi scheduled DA QUA (gio bat dau VN <= hien tai) trong range — nhu attendance/stats
        Instant now = Instant.now();
        List<ClassSession> pastSessions = reportSessions.findScheduledInRange(classId, from, to).stream()
                .filter(s -> !TimeUtil.sessionStartUtc(s.getSessionDate(), s.getStartTime()).isAfter(now))
                .toList();
        List<Long> sessionIds = pastSessions.stream().map(ClassSession::getId).toList();

        // [studentId, status, count] -> map de tra cuu tung HS
        Map<Long, Map<String, Long>> countsByStudent = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            for (Object[] row : attendance.countByStudentAndStatus(sessionIds)) {
                countsByStudent.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                        .put((String) row[1], (Long) row[2]);
            }
        }

        // HS dang hoc lop (enrollment mo), sap theo ten cho de doc
        List<Long> studentIds = enrollments.findByClassIdAndLeftAtIsNull(classId).stream()
                .map(Enrollment::getStudentId)
                .toList();
        List<Student> roster = new ArrayList<>(students.findAllById(studentIds));
        roster.sort(Comparator.comparing(Student::getFullName).thenComparing(Student::getId));

        long totalSessions = pastSessions.size();
        List<AttendanceStatsItem> items = new ArrayList<>();
        for (Student st : roster) {
            Map<String, Long> counts = countsByStudent.getOrDefault(st.getId(), Map.of());
            long present = counts.getOrDefault("present", 0L);
            long absent = counts.getOrDefault("absent", 0L);
            long late = counts.getOrDefault("late", 0L);
            long excused = counts.getOrDefault("excused", 0L);
            // Ty le di hoc: muon van la co di hoc (present + late)
            double rate = totalSessions == 0 ? 0.0
                    : round2((present + late) * 100.0 / totalSessions);
            items.add(new AttendanceStatsItem(st.getId(), st.getFullName(),
                    present, absent, late, excused, totalSessions, rate));
        }

        double avgRate = items.isEmpty() ? 0.0
                : round2(items.stream().mapToDouble(AttendanceStatsItem::attendanceRate).average().orElse(0.0));

        // HS vang nhieu: absent >= threshold, vang nhieu nhat len dau
        List<AttendanceStatsItem> frequentAbsentees = items.stream()
                .filter(i -> i.absent() >= threshold)
                .sorted(Comparator.comparingLong(AttendanceStatsItem::absent).reversed()
                        .thenComparing(AttendanceStatsItem::fullName))
                .toList();

        return new AttendanceReportResponse(
                new AttendanceReportResponse.Summary(totalSessions, avgRate), items, frequentAbsentees);
    }

    // ===== FR-7.3: doanh thu thang (FR-6.5: tro giang KHONG xem duoc) =====

    public RevenueReportResponse revenueReport(User me, Integer year, Integer month, Long classId) {
        // Quyen chan truoc khi validate input? Khong duoc: chua biet lop nao de check ->
        // check theo tung nhanh ngay khi xac dinh duoc pham vi.
        List<SchoolClass> targetClasses;
        List<MonthlyFee> fees;

        if (classId != null) {
            // Admin hoac GV chinh cua dung lop do; tro giang dinh 403 MANAGER_DENIED
            access.assertClassManager(me, classId);
            requireYearMonth(year, month);
            SchoolClass c = classes.findById(classId)
                    .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));
            targetClasses = List.of(c);
            fees = monthlyFees.findByClassIdAndFeeYearAndFeeMonth(classId, year, month);
        } else if (ClassAccessService.isAdmin(me)) {
            // Admin: toan trung tam — cac lop co khoan phi trong thang
            requireYearMonth(year, month);
            fees = monthlyFees.findByFeeYearAndFeeMonth(year, month);
            LinkedHashSet<Long> classIds = fees.stream()
                    .map(MonthlyFee::getClassId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            targetClasses = classes.findAllById(classIds);
        } else {
            // GV chinh: tra cac lop minh la GV chinh (ke ca lop chua co khoan phi -> dong 0).
            // Khong la GV chinh lop nao (tro giang...) -> 403
            List<Long> mainClassIds = classStaff.findByUserIdAndStaffRole(me.getId(), "main").stream()
                    .map(ClassStaff::getClassId)
                    .distinct()
                    .toList();
            if (mainClassIds.isEmpty()) {
                throw new ApiException(403, ClassAccessService.MANAGER_DENIED);
            }
            requireYearMonth(year, month);
            targetClasses = classes.findAllById(mainClassIds);
            fees = monthlyFees.findByClassIdInAndFeeYearAndFeeMonth(mainClassIds, year, month);
        }

        // Tong da dong theo tung khoan phi — [monthlyFeeId, sum]
        Map<Long, BigDecimal> paidByFee = new HashMap<>();
        List<Long> feeIds = fees.stream().map(MonthlyFee::getId).toList();
        if (!feeIds.isEmpty()) {
            for (Object[] row : payments.sumByMonthlyFeeIds(feeIds)) {
                paidByFee.put((Long) row[0], (BigDecimal) row[1]);
            }
        }

        Map<Long, List<MonthlyFee>> feesByClass = fees.stream()
                .collect(Collectors.groupingBy(MonthlyFee::getClassId));

        List<SchoolClass> sortedClasses = new ArrayList<>(targetClasses);
        sortedClasses.sort(Comparator.comparing(SchoolClass::getId));

        List<RevenueReportResponse.RevenueItem> items = new ArrayList<>();
        BigDecimal grandTotalDue = BigDecimal.ZERO;
        BigDecimal grandTotalPaid = BigDecimal.ZERO;
        for (SchoolClass c : sortedClasses) {
            BigDecimal totalDue = BigDecimal.ZERO;
            BigDecimal totalPaid = BigDecimal.ZERO;
            long unpaidCount = 0;
            long partialCount = 0;
            long paidCount = 0;
            for (MonthlyFee f : feesByClass.getOrDefault(c.getId(), List.of())) {
                totalDue = totalDue.add(feeStatus.amountDue(f));
                totalPaid = totalPaid.add(paidByFee.getOrDefault(f.getId(), BigDecimal.ZERO));
                switch (f.getStatus()) {
                    case "paid" -> paidCount++;
                    case "partial" -> partialCount++;
                    default -> unpaidCount++;
                }
            }
            items.add(new RevenueReportResponse.RevenueItem(c.getId(), c.getName(),
                    totalDue, totalPaid, unpaidCount, partialCount, paidCount));
            grandTotalDue = grandTotalDue.add(totalDue);
            grandTotalPaid = grandTotalPaid.add(totalPaid);
        }

        return new RevenueReportResponse(items, grandTotalPaid, grandTotalDue);
    }

    // ===== FR-7.4: dashboard tong quan (admin only) =====

    public DashboardResponse dashboard(User me) {
        if (!ClassAccessService.isAdmin(me)) {
            throw new ApiException(403, ADMIN_DENIED);
        }

        // "Thang nay" theo gio VN (NFR-7)
        LocalDate today = LocalDate.now(TimeUtil.VN_ZONE);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        long activeStudents = students.countByStatus("active");

        // Luon tra du 4 khoi 6-9 (0 neu khong co lop active), khoi la them (neu co) van hien
        Map<String, Long> classesByGrade = new TreeMap<>();
        for (int grade = 6; grade <= 9; grade++) {
            classesByGrade.put(String.valueOf(grade), 0L);
        }
        for (Object[] row : classes.countActiveByGrade()) {
            classesByGrade.put(String.valueOf(row[0]), (Long) row[1]);
        }

        long newStudentsThisMonth = students.countByEnrollmentDateBetween(monthStart, monthEnd);
        long leftStudents = students.countByStatus("left");
        // Chua thu du = unpaid + partial (status != paid)
        long unpaidFeesThisMonth = monthlyFees.countByFeeYearAndFeeMonthAndStatusNot(
                today.getYear(), today.getMonthValue(), "paid");

        return new DashboardResponse(activeStudents, classesByGrade,
                newStudentsThisMonth, leftStudents, unpaidFeesThisMonth);
    }

    // ===== helpers =====

    private static void requireYearMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            throw new ApiException(400, "Phải truyền year và month");
        }
        if (month < 1 || month > 12) {
            throw new ApiException(400, "Tháng không hợp lệ (1-12)");
        }
    }

    private static LocalDate parseDate(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " phải là ngày dạng YYYY-MM-DD");
        }
    }

    // Mo phong `parseInt(threshold) || 3` ben JS: thieu/khong parse duoc/bang 0 -> mac dinh 3
    private static int parseThreshold(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ABSENT_THRESHOLD;
        }
        try {
            int threshold = Integer.parseInt(value.trim());
            return threshold == 0 ? DEFAULT_ABSENT_THRESHOLD : threshold;
        } catch (NumberFormatException e) {
            return DEFAULT_ABSENT_THRESHOLD;
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
