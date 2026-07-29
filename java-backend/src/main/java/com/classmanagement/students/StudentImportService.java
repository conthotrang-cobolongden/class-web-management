package com.classmanagement.students;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.StudentImportError;
import com.classmanagement.students.dto.StudentImportResponse;
import com.classmanagement.user.User;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StudentImportService {

    private static final String SHEET_NAME = "Danh sách học sinh";
    private static final String[] HEADERS = {
            "Họ và tên", "Ngày sinh", "Giới tính", "Trường đang học", "Tên phụ huynh",
            "SĐT phụ huynh", "Ngày vào học", "Học phí riêng", "Ghi chú"
    };
    private static final DateTimeFormatter IMPORT_DATE = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, String> GENDERS = Map.of(
            "nam", "male",
            "nữ", "female",
            "nu", "female",
            "khác", "other",
            "khac", "other",
            "male", "male",
            "female", "female",
            "other", "other");

    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final ClassAccessService access;
    private final AuditService audit;

    public StudentImportService(StudentRepository students,
                                EnrollmentRepository enrollments,
                                SchoolClassRepository classes,
                                ClassAccessService access,
                                AuditService audit) {
        this.students = students;
        this.enrollments = enrollments;
        this.classes = classes;
        this.access = access;
        this.audit = audit;
    }

    public byte[] createTemplate(User me, Long classId) {
        SchoolClass schoolClass = permittedActiveClass(me, classId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(SHEET_NAME);
            data.setDisplayGridlines(false);
            data.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            Row header = data.createRow(0);
            header.setHeightInPoints(26);
            for (int index = 0; index < HEADERS.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(HEADERS[index]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));
            CellStyle feeStyle = workbook.createCellStyle();
            feeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));
            for (int rowIndex = 1; rowIndex <= 500; rowIndex++) {
                Row row = data.createRow(rowIndex);
                row.createCell(1).setCellStyle(dateStyle);
                row.createCell(6).setCellStyle(dateStyle);
                row.createCell(7).setCellStyle(feeStyle);
            }

            DataValidationHelper validationHelper = data.getDataValidationHelper();
            DataValidationConstraint genders = validationHelper.createExplicitListConstraint(
                    new String[]{"Nam", "Nữ", "Khác"});
            DataValidation genderValidation = validationHelper.createValidation(
                    genders, new CellRangeAddressList(1, 500, 2, 2));
            genderValidation.setShowErrorBox(true);
            genderValidation.createErrorBox("Giới tính không hợp lệ", "Chọn Nam, Nữ hoặc Khác.");
            data.addValidationData(genderValidation);

            int[] widths = {28, 14, 13, 24, 24, 19, 14, 18, 34};
            for (int index = 0; index < widths.length; index++) {
                data.setColumnWidth(index, widths[index] * 256);
            }
            data.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 500, 0, 8));

            Sheet guide = workbook.createSheet("Hướng dẫn");
            guide.setDisplayGridlines(false);
            guide.setColumnWidth(0, 24 * 256);
            guide.setColumnWidth(1, 84 * 256);
            String[][] instructions = {
                    {"Mẫu nhập học sinh", "Lớp: " + schoolClass.getName()},
                    {"Định dạng ngày", "dd/MM/yyyy, ví dụ 28/07/2026"},
                    {"Bắt buộc", "Họ và tên, Ngày sinh, SĐT phụ huynh, Ngày vào học"},
                    {"Giới tính", "Để trống hoặc chọn Nam, Nữ, Khác"},
                    {"Học phí riêng", "Để trống để dùng học phí mặc định của lớp; nếu nhập phải là số không âm"},
                    {"Lưu ý", "Không thêm cột lớp. Nếu có bất kỳ lỗi nào, toàn bộ file sẽ không được nhập."}
            };
            for (int rowIndex = 0; rowIndex < instructions.length; rowIndex++) {
                Row row = guide.createRow(rowIndex);
                row.createCell(0).setCellValue(instructions[rowIndex][0]);
                row.createCell(1).setCellValue(instructions[rowIndex][1]);
                row.getCell(0).getCellStyle().setWrapText(true);
                row.getCell(1).getCellStyle().setWrapText(true);
            }
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            guide.getRow(0).getCell(0).setCellStyle(titleStyle);
            guide.getRow(0).getCell(1).setCellStyle(titleStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file mẫu Excel", exception);
        }
    }

    @Transactional
    public StudentImportResponse importWorkbook(User me, Long classId, MultipartFile file) {
        SchoolClass schoolClass = permittedActiveClass(me, classId);
        if (file == null || file.isEmpty()) {
            return StudentImportResponse.invalid(List.of(
                    new StudentImportError(1, "Tệp", "Vui lòng chọn file Excel .xlsx có dữ liệu")));
        }
        String originalFilename = safeFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return StudentImportResponse.invalid(List.of(
                    new StudentImportError(1, "Tệp", "Chỉ hỗ trợ file Excel định dạng .xlsx")));
        }

        ParsedWorkbook parsed = parse(file);
        if (!parsed.errors().isEmpty()) {
            return StudentImportResponse.invalid(parsed.errors());
        }

        List<StudentImportError> duplicateErrors = findDuplicates(parsed.rows());
        if (!duplicateErrors.isEmpty()) {
            return StudentImportResponse.invalid(duplicateErrors);
        }

        List<String> codes = new ArrayList<>();
        for (ImportRow row : parsed.rows()) {
            Student student = new Student();
            student.setFullName(row.fullName());
            student.setDob(row.dob());
            student.setGender(row.gender());
            student.setSchoolName(row.schoolName());
            student.setParentName(row.parentName());
            student.setParentPhone(row.parentPhone());
            student.setEnrollmentDate(row.enrollmentDate());
            student.setStatus("active");
            student.setNote(row.note());
            students.saveAndFlush(student);
            student.setStudentCode(StudentCodes.fromId(student.getId()));
            students.save(student);

            Enrollment enrollment = new Enrollment();
            enrollment.setStudentId(student.getId());
            enrollment.setClassId(classId);
            enrollment.setJoinedAt(row.enrollmentDate());
            enrollment.setCustomMonthlyFee(row.customMonthlyFee());
            enrollment.setCreatedBy(me.getId());
            enrollments.save(enrollment);
            Map<String, Object> enrollmentValues = new LinkedHashMap<>();
            enrollmentValues.put("student_id", student.getId());
            enrollmentValues.put("class_id", classId);
            enrollmentValues.put("joined_at", enrollment.getJoinedAt());
            enrollmentValues.put("custom_monthly_fee", enrollment.getCustomMonthlyFee());
            audit.audit("students", "enrollments", enrollment.getId(), "create",
                    "Xếp lớp khi import " + student.getStudentCode(), null,
                    enrollmentValues, me.getId());

            codes.add(student.getStudentCode());
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("student_code", student.getStudentCode());
            created.put("full_name", student.getFullName());
            created.put("dob", student.getDob());
            created.put("parent_phone", student.getParentPhone());
            created.put("class_id", classId);
            audit.audit("students", student.getId(), "create", null, created, me.getId());
        }

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("filename", originalFilename);
        batch.put("class_id", classId);
        batch.put("class_name", schoolClass.getName());
        batch.put("count", codes.size());
        batch.put("student_codes", codes);
        audit.audit("imports", null, "import", null, batch, me.getId());
        return new StudentImportResponse(codes.size(), codes, List.of());
    }

    private SchoolClass permittedActiveClass(User me, Long classId) {
        access.assertClassStaffOrAdmin(me, classId);
        SchoolClass schoolClass = classes.findById(classId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy lớp học"));
        if (!"active".equals(schoolClass.getStatus())) {
            throw new ApiException(400, "Chỉ có thể nhập học sinh vào lớp đang hoạt động");
        }
        return schoolClass;
    }

    private ParsedWorkbook parse(MultipartFile file) {
        List<StudentImportError> errors = new ArrayList<>();
        List<ImportRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return new ParsedWorkbook(List.of(), List.of(
                        new StudentImportError(1, "Tệp", "File Excel không có trang dữ liệu")));
            }
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            validateHeaders(sheet.getRow(0), formatter, evaluator, errors);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row excelRow = sheet.getRow(index);
                if (excelRow == null || isBlank(excelRow, formatter, evaluator)) {
                    continue;
                }
                ImportRow row = parseRow(index + 1, excelRow, formatter, evaluator, errors);
                if (row != null) {
                    rows.add(row);
                }
            }
            if (rows.isEmpty() && errors.isEmpty()) {
                errors.add(new StudentImportError(2, "Họ và tên", "File chưa có học sinh để nhập"));
            }
        } catch (Exception exception) {
            errors.add(new StudentImportError(1, "Tệp", "Không đọc được file Excel .xlsx hợp lệ"));
        }
        return new ParsedWorkbook(rows, errors);
    }

    private static void validateHeaders(Row header, DataFormatter formatter,
                                        FormulaEvaluator evaluator, List<StudentImportError> errors) {
        for (int column = 0; column < HEADERS.length; column++) {
            String actual = text(header == null ? null : header.getCell(column), formatter, evaluator);
            if (!HEADERS[column].equals(actual)) {
                errors.add(new StudentImportError(1, HEADERS[column],
                        "Tiêu đề cột phải là ‘" + HEADERS[column] + "’"));
            }
        }
    }

    private static ImportRow parseRow(int rowNumber, Row row, DataFormatter formatter,
                                      FormulaEvaluator evaluator, List<StudentImportError> errors) {
        int before = errors.size();
        String fullName = requiredText(row, 0, rowNumber, formatter, evaluator, errors);
        LocalDate dob = requiredDate(row, 1, rowNumber, formatter, evaluator, errors);
        String gender = gender(row, rowNumber, formatter, evaluator, errors);
        String schoolName = optionalText(row, 3, formatter, evaluator);
        String parentName = optionalText(row, 4, formatter, evaluator);
        String parentPhone = requiredText(row, 5, rowNumber, formatter, evaluator, errors);
        LocalDate enrollmentDate = requiredDate(row, 6, rowNumber, formatter, evaluator, errors);
        BigDecimal customFee = fee(row, rowNumber, formatter, evaluator, errors);
        String note = optionalText(row, 8, formatter, evaluator);

        maxLength(fullName, 100, rowNumber, HEADERS[0], errors);
        maxLength(schoolName, 150, rowNumber, HEADERS[3], errors);
        maxLength(parentName, 100, rowNumber, HEADERS[4], errors);
        maxLength(parentPhone, 20, rowNumber, HEADERS[5], errors);
        maxLength(note, 1000, rowNumber, HEADERS[8], errors);
        if (errors.size() != before) {
            return null;
        }
        return new ImportRow(rowNumber, collapseWhitespace(fullName), dob, gender,
                emptyToNull(schoolName), emptyToNull(parentName), collapseWhitespace(parentPhone),
                enrollmentDate, customFee, emptyToNull(note));
    }

    private List<StudentImportError> findDuplicates(List<ImportRow> rows) {
        List<StudentImportError> errors = new ArrayList<>();
        Map<DuplicateKey, Integer> firstRows = new HashMap<>();
        for (ImportRow row : rows) {
            DuplicateKey key = duplicateKey(row.fullName(), row.dob(), row.parentPhone());
            Integer firstRow = firstRows.putIfAbsent(key, row.rowNumber());
            if (firstRow != null) {
                errors.add(new StudentImportError(row.rowNumber(), HEADERS[0],
                        "Học sinh trùng với dòng " + firstRow + " trong file"));
            }
        }

        Set<LocalDate> dates = new LinkedHashSet<>();
        rows.forEach(row -> dates.add(row.dob()));
        Set<DuplicateKey> existing = new HashSet<>();
        if (!dates.isEmpty()) {
            students.findByDobIn(dates).forEach(student -> existing.add(
                    duplicateKey(student.getFullName(), student.getDob(), student.getParentPhone())));
        }
        for (ImportRow row : rows) {
            if (existing.contains(duplicateKey(row.fullName(), row.dob(), row.parentPhone()))) {
                errors.add(new StudentImportError(row.rowNumber(), HEADERS[0],
                        "Học sinh đã tồn tại (trùng họ tên, ngày sinh và SĐT phụ huynh)"));
            }
        }
        return errors;
    }

    private static String requiredText(Row row, int column, int rowNumber,
                                       DataFormatter formatter, FormulaEvaluator evaluator,
                                       List<StudentImportError> errors) {
        String value = text(row.getCell(column), formatter, evaluator);
        if (value.isBlank()) {
            errors.add(new StudentImportError(rowNumber, HEADERS[column], "Trường bắt buộc không được để trống"));
        }
        return value;
    }

    private static String optionalText(Row row, int column, DataFormatter formatter,
                                       FormulaEvaluator evaluator) {
        return text(row.getCell(column), formatter, evaluator);
    }

    private static LocalDate requiredDate(Row row, int column, int rowNumber,
                                          DataFormatter formatter, FormulaEvaluator evaluator,
                                          List<StudentImportError> errors) {
        Cell cell = row.getCell(column);
        String rendered = text(cell, formatter, evaluator);
        if (rendered.isBlank()) {
            errors.add(new StudentImportError(rowNumber, HEADERS[column], "Trường bắt buộc không được để trống"));
            return null;
        }
        try {
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return LocalDate.parse(rendered, IMPORT_DATE);
        } catch (DateTimeParseException | IllegalStateException exception) {
            errors.add(new StudentImportError(rowNumber, HEADERS[column], "Ngày phải đúng định dạng dd/MM/yyyy"));
            return null;
        }
    }

    private static String gender(Row row, int rowNumber, DataFormatter formatter,
                                 FormulaEvaluator evaluator, List<StudentImportError> errors) {
        String value = text(row.getCell(2), formatter, evaluator);
        if (value.isBlank()) {
            return null;
        }
        String mapped = GENDERS.get(value.toLowerCase(Locale.ROOT));
        if (mapped == null) {
            errors.add(new StudentImportError(rowNumber, HEADERS[2], "Chỉ chấp nhận Nam, Nữ hoặc Khác"));
        }
        return mapped;
    }

    private static BigDecimal fee(Row row, int rowNumber, DataFormatter formatter,
                                  FormulaEvaluator evaluator, List<StudentImportError> errors) {
        Cell cell = row.getCell(7);
        String rendered = text(cell, formatter, evaluator);
        if (rendered.isBlank()) {
            return null;
        }
        try {
            BigDecimal value;
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                value = BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                value = new BigDecimal(rendered.replace(" ", ""));
            }
            value = value.setScale(0, RoundingMode.UNNECESSARY);
            if (value.signum() < 0) {
                throw new ArithmeticException("negative");
            }
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            errors.add(new StudentImportError(rowNumber, HEADERS[7], "Học phí riêng phải là số nguyên không âm"));
            return null;
        }
    }

    private static boolean isBlank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int column = 0; column < HEADERS.length; column++) {
            if (!text(row.getCell(column), formatter, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String text(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private static void maxLength(String value, int maximum, int row, String column,
                                  List<StudentImportError> errors) {
        if (value != null && value.length() > maximum) {
            errors.add(new StudentImportError(row, column, "Tối đa " + maximum + " ký tự"));
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : collapseWhitespace(value);
    }

    static String normalizeName(String value) {
        return collapseWhitespace(value).toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("[\\s.\\-]", "");
    }

    private static String collapseWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static DuplicateKey duplicateKey(String fullName, LocalDate dob, String phone) {
        return new DuplicateKey(normalizeName(fullName), dob, normalizePhone(phone));
    }

    private static String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "danh-sach-hoc-sinh.xlsx";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return filename.replaceAll("[\\p{Cntrl}]", "").trim();
    }

    private record ParsedWorkbook(List<ImportRow> rows, List<StudentImportError> errors) {
    }

    private record ImportRow(int rowNumber, String fullName, LocalDate dob, String gender,
                             String schoolName, String parentName, String parentPhone,
                             LocalDate enrollmentDate, BigDecimal customMonthlyFee, String note) {
    }

    private record DuplicateKey(String fullName, LocalDate dob, String parentPhone) {
    }
}
