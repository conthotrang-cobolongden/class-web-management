package com.classmanagement.fees;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.FeeStatusService;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.MonthlyFee;
import com.classmanagement.domain.MonthlyFeeRepository;
import com.classmanagement.domain.Payment;
import com.classmanagement.domain.PaymentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.fees.dto.AdjustFeeRequest;
import com.classmanagement.fees.dto.CreatePaymentRequest;
import com.classmanagement.fees.dto.CreatePaymentResponse;
import com.classmanagement.fees.dto.FeeDto;
import com.classmanagement.fees.dto.GenerateFeesRequest;
import com.classmanagement.fees.dto.GenerateFeesResponse;
import com.classmanagement.fees.dto.PaymentDto;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Hoc phi (FR-6) — port logic tu backend/src/services/fees.js theo spec API.md muc 6
@Service
public class FeeService {

    public static final List<String> METHODS = List.of("cash", "transfer");

    private final MonthlyFeeRepository monthlyFees;
    private final PaymentRepository payments;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final UserRepository users;
    private final ClassAccessService access;
    private final FeeStatusService feeStatus;
    private final AuditService auditService;

    public FeeService(MonthlyFeeRepository monthlyFees, PaymentRepository payments,
                      EnrollmentRepository enrollments, StudentRepository students,
                      SchoolClassRepository classes, UserRepository users,
                      ClassAccessService access, FeeStatusService feeStatus,
                      AuditService auditService) {
        this.monthlyFees = monthlyFees;
        this.payments = payments;
        this.enrollments = enrollments;
        this.students = students;
        this.classes = classes;
        this.users = users;
        this.access = access;
        this.feeStatus = feeStatus;
        this.auditService = auditService;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    // FR-6.2: sinh khoan phi thang cho moi HS active + enrollment mo + lop active.
    // base_amount = custom_monthly_fee (uu dai rieng) ?? default_monthly_fee cua lop.
    // Idempotent: khoan da ton tai (unique student+class+year+month) -> skipped.
    @Transactional
    public GenerateFeesResponse generate(User me, GenerateFeesRequest req) {
        if (!ClassAccessService.isAdmin(me)) {
            throw new ApiException(403, "Bạn không có quyền thực hiện thao tác này");
        }
        int year = req.year();
        int month = req.month();
        if (month < 1 || month > 12) {
            throw new ApiException(400, "Trường month phải trong khoảng 1..12");
        }
        // Cung range voi check constraint ck_monthly_fees_year trong DB
        if (year < 2020 || year > 2100) {
            throw new ApiException(400, "Trường year phải trong khoảng 2020..2100");
        }

        List<Enrollment> open = enrollments.findByLeftAtIsNull();
        Map<Long, Student> studentById = mapById(open.stream().map(Enrollment::getStudentId).collect(Collectors.toSet()));
        Map<Long, SchoolClass> classById = classes.findAllById(
                        open.stream().map(Enrollment::getClassId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolClass::getId, Function.identity()));

        int created = 0;
        int skipped = 0;
        for (Enrollment e : open) {
            Student student = studentById.get(e.getStudentId());
            SchoolClass clazz = classById.get(e.getClassId());
            // FR-6.2b: HS paused/left tu dau thang khong sinh khoan phi; lop dong cung bo qua
            if (student == null || !"active".equals(student.getStatus())
                    || clazz == null || !"active".equals(clazz.getStatus())) {
                continue;
            }
            if (monthlyFees.existsByStudentIdAndClassIdAndFeeYearAndFeeMonth(
                    e.getStudentId(), e.getClassId(), year, month)) {
                skipped++;
                continue;
            }
            MonthlyFee fee = new MonthlyFee();
            fee.setStudentId(e.getStudentId());
            fee.setClassId(e.getClassId());
            fee.setFeeYear(year);
            fee.setFeeMonth(month);
            fee.setBaseAmount(e.getCustomMonthlyFee() != null
                    ? e.getCustomMonthlyFee()
                    : clazz.getDefaultMonthlyFee());
            monthlyFees.save(fee);
            created++;
        }
        return new GenerateFeesResponse(created, skipped);
    }

    // GET /api/fees — admin: tat ca; GV/TG: gioi han trong lop minh (tro giang DUOC xem — FR-6.5
    // chi cam xem doanh thu tong hop, khong cam xem trang thai dong phi tung HS)
    public PageResponse<FeeDto> list(User me, Pagination p, Long classId, Integer year,
                                     Integer month, String status) {
        if (classId != null) {
            access.assertClassMember(me, classId);
        }
        Pageable pageable = PageRequest.of(p.page() - 1, p.pageSize(), Sort.by("id"));
        List<Long> visible = access.visibleClassIds(me);

        Page<MonthlyFee> result;
        if (visible == null) {
            result = monthlyFees.search(classId, year, month, emptyToNull(status), pageable);
        } else if (visible.isEmpty()) {
            result = Page.empty(pageable);
        } else {
            result = monthlyFees.searchWithin(visible, classId, year, month, emptyToNull(status), pageable);
        }
        return new PageResponse<>(toDtos(result.getContent()), result.getTotalElements(),
                p.page(), p.pageSize());
    }

    // FR-6.3: dieu chinh tay so tien phai dong (thang co buoi huy, HS tam nghi...) — reason bat buoc
    @Transactional
    public FeeDto adjust(User me, Long id, AdjustFeeRequest req) {
        MonthlyFee fee = monthlyFees.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy khoản phí"));
        access.assertClassManager(me, fee.getClassId());

        if (req.adjustedAmount() == null) {
            throw new ApiException(400, "Thiếu trường bắt buộc: adjusted_amount");
        }
        if (req.adjustedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(400, "Số tiền điều chỉnh không được âm");
        }
        if (req.adjustmentReason() == null || req.adjustmentReason().isBlank()) {
            throw new ApiException(400, "Phải ghi lý do điều chỉnh");
        }

        Map<String, Object> oldValues = snapshot(fee);
        fee.setAdjustedAmount(req.adjustedAmount());
        fee.setAdjustmentReason(req.adjustmentReason());
        fee.setAdjustedBy(me.getId());
        monthlyFees.save(fee);
        // Tinh lai status theo tong da dong so voi so tien moi (paid co the du/thieu sau dieu chinh)
        feeStatus.refreshFeeStatus(fee);
        auditService.audit("monthly_fees", fee.getId(), "update", oldValues, snapshot(fee), me.getId());

        return toDtos(List.of(fee)).get(0);
    }

    // FR-6.4: ghi nhan 1 lan dong tien (cho phep thieu/thua) — tro giang cung lam duoc
    @Transactional
    public CreatePaymentResponse addPayment(User me, Long id, CreatePaymentRequest req) {
        MonthlyFee fee = monthlyFees.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy khoản phí"));
        access.assertClassMember(me, fee.getClassId());

        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(400, "Số tiền phải lớn hơn 0");
        }
        if (!METHODS.contains(req.method())) {
            throw new ApiException(400, "Trường method phải là một trong: " + String.join(", ", METHODS));
        }

        Payment payment = new Payment();
        payment.setMonthlyFeeId(fee.getId());
        payment.setAmount(req.amount());
        payment.setMethod(req.method());
        payment.setPaidAt(req.paidAt());
        payment.setRecordedBy(me.getId());
        payment.setNote(emptyToNull(req.note()));
        payments.save(payment);

        String status = feeStatus.refreshFeeStatus(fee);
        return new CreatePaymentResponse(PaymentDto.from(payment, me.getFullName()), status);
    }

    public List<PaymentDto> listPayments(User me, Long id) {
        MonthlyFee fee = monthlyFees.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy khoản phí"));
        access.assertClassMember(me, fee.getClassId());

        List<Payment> list = payments.findByMonthlyFeeIdOrderByPaidAtAscIdAsc(fee.getId());
        Map<Long, String> nameByUserId = users.findAllById(
                        list.stream().map(Payment::getRecordedBy).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream()
                .map(pm -> PaymentDto.from(pm, nameByUserId.get(pm.getRecordedBy())))
                .toList();
    }

    // Gan ten HS/lop + tong da dong cho tung khoan phi (batch 3 query, tranh N+1)
    private List<FeeDto> toDtos(List<MonthlyFee> fees) {
        if (fees.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentById = mapById(
                fees.stream().map(MonthlyFee::getStudentId).collect(Collectors.toSet()));
        Map<Long, String> classNameById = classes.findAllById(
                        fees.stream().map(MonthlyFee::getClassId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));
        Map<Long, BigDecimal> paidByFeeId = payments.sumByMonthlyFeeIds(
                        fees.stream().map(MonthlyFee::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), FeeService::toBigDecimal));

        return fees.stream()
                .map(f -> FeeDto.from(f,
                        studentById.containsKey(f.getStudentId())
                                ? studentById.get(f.getStudentId()).getFullName() : null,
                        classNameById.get(f.getClassId()),
                        paidByFeeId.getOrDefault(f.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private Map<Long, Student> mapById(Set<Long> studentIds) {
        return students.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
    }

    private static BigDecimal toBigDecimal(Object[] row) {
        return row[1] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(row[1]));
    }

    // Snapshot cac truong lien quan dieu chinh phi cho audit (NFR-4: gia tri cu/moi)
    private static Map<String, Object> snapshot(MonthlyFee fee) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("adjusted_amount", fee.getAdjustedAmount());
        values.put("adjustment_reason", fee.getAdjustmentReason());
        values.put("adjusted_by", fee.getAdjustedBy());
        values.put("status", fee.getStatus());
        return values;
    }
}
