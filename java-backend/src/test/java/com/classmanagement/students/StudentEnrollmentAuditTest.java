package com.classmanagement.students;

import com.classmanagement.TestUsers;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.CreateStudentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentEnrollmentAuditTest {

    @Test
    void creatingStudentWithClassAuditsTheEnrollmentAndCustomFee() {
        StudentRepository students = mock(StudentRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        SchoolClassRepository classes = mock(SchoolClassRepository.class);
        AuditService audit = mock(AuditService.class);
        SchoolClass schoolClass = new SchoolClass();
        ReflectionTestUtils.setField(schoolClass, "id", 7L);
        schoolClass.setName("Lớp 8A");
        schoolClass.setStatus("active");
        when(classes.findById(7L)).thenReturn(Optional.of(schoolClass));
        doAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            ReflectionTestUtils.setField(student, "id", 42L);
            return student;
        }).when(students).save(any(Student.class));
        doAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", 80L);
            return enrollment;
        }).when(enrollments).save(any(Enrollment.class));

        StudentService service = new StudentService(
                students, enrollments, mock(StudentEnrollmentRepository.class), classes,
                mock(ClassAccessService.class), audit);
        CreateStudentRequest request = new CreateStudentRequest(
                "Nguyễn Văn An", LocalDate.of(2012, 5, 20), "male", "THCS A",
                "Nguyễn Văn B", "0901234567", LocalDate.of(2026, 7, 28),
                null, null, 7L, new BigDecimal("500000"));

        service.create(TestUsers.admin(1L), request);

        verify(audit).audit(eq("students"), eq("enrollments"), eq(80L), eq("create"),
                contains("Xếp lớp"), isNull(), any(), eq(1L));
    }
}