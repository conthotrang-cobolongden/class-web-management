package com.classmanagement.students;

import com.classmanagement.TestUsers;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.CreateStudentRequest;
import com.classmanagement.students.dto.StudentDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class StudentCreationCodeTest {

    @Test
    void assignsCodeImmediatelyAfterIdentityInsert() {
        StudentRepository students = mock(StudentRepository.class);
        doAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            ReflectionTestUtils.setField(student, "id", 42L);
            return student;
        }).when(students).save(any(Student.class));

        StudentService service = new StudentService(
                students,
                mock(EnrollmentRepository.class),
                mock(StudentEnrollmentRepository.class),
                mock(SchoolClassRepository.class),
                mock(ClassAccessService.class),
                mock(AuditService.class));

        CreateStudentRequest request = new CreateStudentRequest(
                "Nguyễn Văn An",
                LocalDate.of(2012, 5, 20),
                "male",
                "THCS A",
                "Nguyễn Văn B",
                "0901234567",
                LocalDate.of(2026, 7, 28),
                null,
                null,
                null,
                null);

        StudentDto created = service.create(TestUsers.admin(1L), request);

        assertEquals("HS000042", created.studentCode());
    }
}
