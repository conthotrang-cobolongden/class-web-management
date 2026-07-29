package com.classmanagement.students;

import com.classmanagement.TestUsers;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.StudentImportResponse;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportServiceTest {

    private StudentRepository students;
    private EnrollmentRepository enrollments;
    private SchoolClassRepository classes;
    private ClassAccessService access;
    private AuditService audit;
    private StudentImportService service;
    private SchoolClass activeClass;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepository.class);
        enrollments = mock(EnrollmentRepository.class);
        classes = mock(SchoolClassRepository.class);
        access = mock(ClassAccessService.class);
        audit = mock(AuditService.class);
        service = new StudentImportService(students, enrollments, classes, access, audit);

        activeClass = new SchoolClass();
        ReflectionTestUtils.setField(activeClass, "id", 7L);
        activeClass.setName("Lớp 8A");
        activeClass.setStatus("active");
        when(classes.findById(7L)).thenReturn(Optional.of(activeClass));
        when(students.findByDobIn(any())).thenReturn(List.of());

        AtomicLong ids = new AtomicLong(40);
        doAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            if (student.getId() == null) {
                ReflectionTestUtils.setField(student, "id", ids.incrementAndGet());
            }
            return student;
        }).when(students).saveAndFlush(any(Student.class));
        doAnswer(invocation -> invocation.getArgument(0))
                .when(students).save(any(Student.class));
        AtomicLong enrollmentIds = new AtomicLong(80);
        doAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", enrollmentIds.incrementAndGet());
            return enrollment;
        }).when(enrollments).save(any(Enrollment.class));
    }

    @Test
    void createsClassScopedTemplateWithHeadersInstructionsAndGenderValidation() throws Exception {
        var assistant = TestUsers.assistant(5L);
        byte[] bytes = service.createTemplate(assistant, 7L);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Danh sách học sinh", workbook.getSheetAt(0).getSheetName());
            assertEquals("Họ và tên", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Ghi chú", workbook.getSheetAt(0).getRow(0).getCell(8).getStringCellValue());
            assertFalse(workbook.getSheetAt(0).getDataValidations().isEmpty());
            assertEquals("Hướng dẫn", workbook.getSheetAt(1).getSheetName());
        }
        verify(access).assertClassStaffOrAdmin(assistant, 7L);
    }

    @Test
    void importsValidRowsAndReturnsGeneratedCodes() throws Exception {
        MockMultipartFile file = workbook(
                row("Nguyễn Văn An", "20/05/2012", "Nam", "THCS A", "Nguyễn Văn B", "0901 234 567", "28/07/2026", "500000", "Học buổi tối"),
                row("Trần Thị Bình", "01/09/2013", "Nữ", "", "Trần Văn C", "0902.345.678", "28/07/2026", "", ""));

        StudentImportResponse result = service.importWorkbook(TestUsers.teacher(2L), 7L, file);

        assertEquals(2, result.imported());
        assertEquals(List.of("HS000041", "HS000042"), result.studentCodes());
        assertTrue(result.errors().isEmpty());
        verify(students, org.mockito.Mockito.times(2)).saveAndFlush(any(Student.class));
        verify(enrollments, org.mockito.Mockito.times(2)).save(any(Enrollment.class));
        verify(audit, org.mockito.Mockito.times(2)).audit(
                org.mockito.ArgumentMatchers.eq("students"),
                org.mockito.ArgumentMatchers.eq("enrollments"),
                anyLong(), org.mockito.ArgumentMatchers.eq("create"), any(String.class),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(2L));
    }

    @Test
    void rejectsMixedWorkbookWithoutWritingAnyRows() throws Exception {
        MockMultipartFile file = workbook(
                row("Nguyễn Văn An", "20/05/2012", "Nam", "", "", "0901234567", "28/07/2026", "", ""),
                row("", "31/02/2012", "Không rõ", "", "", "", "", "-1", ""));

        StudentImportResponse result = service.importWorkbook(TestUsers.admin(1L), 7L, file);

        assertEquals(0, result.imported());
        assertTrue(result.errors().stream().anyMatch(error -> error.row() == 3 && error.column().equals("Họ và tên")));
        assertTrue(result.errors().stream().anyMatch(error -> error.column().equals("Ngày sinh")));
        assertTrue(result.errors().stream().anyMatch(error -> error.column().equals("Giới tính")));
        assertTrue(result.errors().stream().anyMatch(error -> error.column().equals("SĐT phụ huynh")));
        assertTrue(result.errors().stream().anyMatch(error -> error.column().equals("Ngày vào học")));
        assertTrue(result.errors().stream().anyMatch(error -> error.column().equals("Học phí riêng")));
        verify(students, never()).saveAndFlush(any(Student.class));
        verify(enrollments, never()).save(any(Enrollment.class));
    }

    @Test
    void rejectsDuplicateRowsAfterNameAndPhoneNormalization() throws Exception {
        MockMultipartFile file = workbook(
                row(" Nguyễn   Văn An ", "20/05/2012", "Nam", "", "", "0901-234-567", "28/07/2026", "", ""),
                row("nguyễn văn an", "20/05/2012", "Nam", "", "", "0901.234.567", "28/07/2026", "", ""));

        StudentImportResponse result = service.importWorkbook(TestUsers.admin(1L), 7L, file);

        assertEquals(0, result.imported());
        assertTrue(result.errors().stream().anyMatch(error -> error.row() == 3 && error.reason().contains("trùng")));
        verify(students, never()).saveAndFlush(any(Student.class));
    }

    @Test
    void rejectsDatabaseDuplicate() throws Exception {
        Student existing = new Student();
        existing.setFullName("Nguyễn Văn An");
        existing.setDob(LocalDate.of(2012, 5, 20));
        existing.setParentPhone("0901234567");
        when(students.findByDobIn(any())).thenReturn(List.of(existing));

        StudentImportResponse result = service.importWorkbook(
                TestUsers.admin(1L), 7L,
                workbook(row("nguyễn văn an", "20/05/2012", "Nam", "", "", "0901 234 567", "28/07/2026", "", "")));

        assertTrue(result.errors().stream().anyMatch(error -> error.reason().contains("đã tồn tại")));
        verify(students, never()).saveAndFlush(any(Student.class));
    }

    @Test
    void rejectsUnauthorizedClassBeforeReadingWorkbook() throws Exception {
        doThrow(new ApiException(403, "forbidden"))
                .when(access).assertClassStaffOrAdmin(any(), anyLong());

        ApiException error = assertThrows(ApiException.class, () -> service.importWorkbook(
                TestUsers.assistant(5L), 7L,
                workbook(row("Nguyễn Văn An", "20/05/2012", "Nam", "", "", "0901234567", "28/07/2026", "", ""))));

        assertEquals(403, error.getStatus());
        verify(students, never()).saveAndFlush(any(Student.class));
    }

    private static String[] row(String... values) {
        return values;
    }

    private static MockMultipartFile workbook(String[]... rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh sách học sinh");
            var header = sheet.createRow(0);
            String[] headers = {"Họ và tên", "Ngày sinh", "Giới tính", "Trường đang học", "Tên phụ huynh",
                    "SĐT phụ huynh", "Ngày vào học", "Học phí riêng", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file", "danh-sach-hoc-sinh.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
