package com.classmanagement.grades;

import com.classmanagement.TestUsers;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.ClassStaff;
import com.classmanagement.domain.ClassStaffRepository;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.GradeColumn;
import com.classmanagement.domain.GradeColumnRepository;
import com.classmanagement.domain.GradeRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.grades.dto.CreateGradeColumnRequest;
import com.classmanagement.grades.dto.UpdateGradeColumnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeColumnPermissionTest {

    private final GradeColumnRepository columns = mock(GradeColumnRepository.class);
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final ClassStaffRepository staff = mock(ClassStaffRepository.class);
    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(columns, mock(GradeRepository.class), classes,
                mock(EnrollmentRepository.class), mock(StudentRepository.class),
                new ClassAccessService(staff), mock(AuditService.class));
        when(classes.findById(10L)).thenReturn(Optional.of(schoolClass(10L, "active")));
    }

    @Test
    void assignedAssistantCanCreateUpdateAndDeleteGradeColumns() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.of(assignment(10L, 2L)));
        GradeColumn existing = gradeColumn(40L, 10L);
        when(columns.findByIdAndIsDeletedFalse(40L)).thenReturn(Optional.of(existing));

        service.createColumn(TestUsers.assistant(2L), 10L,
                new CreateGradeColumnRequest("Kiểm tra", null, null, null));
        service.updateColumn(TestUsers.assistant(2L), 40L,
                new UpdateGradeColumnRequest(Map.of("name", "Kiểm tra mới")));
        service.deleteColumn(TestUsers.assistant(2L), 40L);

        verify(columns, org.mockito.Mockito.times(3)).save(any(GradeColumn.class));
    }

    @Test
    void unrelatedAssistantCannotCreateGradeColumn() {
        when(staff.findByClassIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.createColumn(
                TestUsers.assistant(2L), 10L,
                new CreateGradeColumnRequest("Kiểm tra", null, null, null)));

        assertEquals(403, error.getStatus());
    }

    private static ClassStaff assignment(long classId, long userId) {
        ClassStaff value = new ClassStaff();
        value.setClassId(classId);
        value.setUserId(userId);
        value.setStaffRole("assistant");
        return value;
    }

    private static GradeColumn gradeColumn(long id, long classId) {
        GradeColumn value = new GradeColumn();
        ReflectionTestUtils.setField(value, "id", id);
        value.setClassId(classId);
        value.setName("Cũ");
        value.setWeight(java.math.BigDecimal.ONE);
        value.setMaxScore(java.math.BigDecimal.TEN);
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
