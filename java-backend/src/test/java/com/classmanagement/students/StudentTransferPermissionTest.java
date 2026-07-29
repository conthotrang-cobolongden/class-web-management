package com.classmanagement.students;

import com.classmanagement.TestUsers;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.ClassStaff;
import com.classmanagement.domain.ClassStaffRepository;
import com.classmanagement.domain.Enrollment;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.Student;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.students.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentTransferPermissionTest {

    private final StudentRepository students = mock(StudentRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final ClassStaffRepository staff = mock(ClassStaffRepository.class);
    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(students, enrollments, mock(StudentEnrollmentRepository.class),
                classes, new ClassAccessService(staff), mock(AuditService.class));
        when(enrollments.findByStudentIdAndLeftAtIsNull(99L)).thenReturn(Optional.of(enrollment()));
        when(students.findById(99L)).thenReturn(Optional.of(new Student()));
    }

    @Test
    void assignedAssistantCanTransferToActiveClassWithoutDestinationMembership() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.of(assignment()));
        when(classes.findById(20L)).thenReturn(Optional.of(schoolClass(20L, "active")));

        service.transfer(TestUsers.assistant(2L), 99L,
                new TransferRequest(20L, LocalDate.of(2026, 7, 28)));

        verify(enrollments).saveAndFlush(any(Enrollment.class));
        verify(enrollments).save(any(Enrollment.class));
        verify(staff, never()).findByClassIdAndUserId(20L, 2L);
    }

    @Test
    void transferOptionsIncludeUnassignedActiveDestination() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.of(assignment()));
        when(classes.findByStatus("active")).thenReturn(List.of(
                schoolClass(10L, "active"), schoolClass(20L, "active")));

        var options = service.transferOptions(TestUsers.assistant(2L), 99L);

        assertEquals(List.of(20L), options.stream()
                .map(StudentService.TransferClassOption::id)
                .toList());
    }

    @Test
    void unrelatedAssistantCannotTransferStudent() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.transfer(
                TestUsers.assistant(2L), 99L,
                new TransferRequest(20L, LocalDate.of(2026, 7, 28))));

        assertEquals(403, error.getStatus());
    }

    @Test
    void inactiveDestinationStillFailsForAssignedAssistant() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.of(assignment()));
        when(classes.findById(20L)).thenReturn(Optional.of(schoolClass(20L, "closed")));

        ApiException error = assertThrows(ApiException.class, () -> service.transfer(
                TestUsers.assistant(2L), 99L,
                new TransferRequest(20L, LocalDate.of(2026, 7, 28))));

        assertEquals(400, error.getStatus());
    }

    private static Enrollment enrollment() {
        Enrollment value = new Enrollment();
        ReflectionTestUtils.setField(value, "id", 50L);
        value.setStudentId(99L);
        value.setClassId(10L);
        value.setJoinedAt(LocalDate.of(2026, 1, 1));
        return value;
    }

    private static ClassStaff assignment() {
        ClassStaff value = new ClassStaff();
        value.setClassId(10L);
        value.setUserId(2L);
        value.setStaffRole("assistant");
        return value;
    }

    private static SchoolClass schoolClass(long id, String status) {
        SchoolClass value = new SchoolClass();
        ReflectionTestUtils.setField(value, "id", id);
        value.setName("Lớp " + id);
        value.setStatus(status);
        return value;
    }
}
