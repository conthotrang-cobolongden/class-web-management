package com.classmanagement.sessions;

import com.classmanagement.TestUsers;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.domain.AttendanceRecordRepository;
import com.classmanagement.domain.ClassSession;
import com.classmanagement.domain.ClassSessionRepository;
import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.sessions.dto.WeeklySessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklySessionServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 27);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 2);

    private final ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final AttendanceRecordRepository attendance = mock(AttendanceRecordRepository.class);
    private final ClassAccessService access = mock(ClassAccessService.class);
    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessions, classes, attendance, access, mock(AuditService.class));
    }

    @Test
    void rejectsRangesThatAreNotExactlyMondayThroughSunday() {
        ApiException wrongStart = assertThrows(ApiException.class, () -> service.week(
                TestUsers.admin(1L), "2026-07-28", "2026-08-03"));
        ApiException wrongLength = assertThrows(ApiException.class, () -> service.week(
                TestUsers.admin(1L), "2026-07-27", "2026-08-01"));

        assertEquals(400, wrongStart.getStatus());
        assertEquals(400, wrongLength.getStatus());
    }

    @Test
    void adminReceivesAllClassesChronologicallyIncludingCancelledAndMakeup() {
        when(access.visibleClassIds(any())).thenReturn(null);
        ClassSession later = session(2L, 20L, MONDAY.plusDays(2), LocalTime.of(18, 0), "cancelled", false);
        ClassSession earlier = session(1L, 10L, MONDAY, LocalTime.of(8, 0), "scheduled", true);
        when(sessions.findBySessionDateBetween(MONDAY, SUNDAY)).thenReturn(List.of(later, earlier));
        when(classes.findAllById(any())).thenReturn(List.of(schoolClass(10L, "Lớp A"), schoolClass(20L, "Lớp B")));
        when(attendance.countBySessionIds(any())).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));

        List<WeeklySessionDto> result = service.week(
                TestUsers.admin(1L), MONDAY.toString(), SUNDAY.toString());

        assertEquals(List.of(1L, 2L), result.stream().map(WeeklySessionDto::id).toList());
        assertEquals("scheduled", result.get(0).status());
        assertEquals(true, result.get(0).isMakeup());
        assertEquals("cancelled", result.get(1).status());
        assertEquals(5L, result.get(0).attendanceCount());
    }

    @Test
    void staffQueryIsRestrictedToAssignedClassIds() {
        when(access.visibleClassIds(any())).thenReturn(List.of(10L));
        when(sessions.findByClassIdInAndSessionDateBetween(List.of(10L), MONDAY, SUNDAY))
                .thenReturn(List.of(session(1L, 10L, MONDAY, LocalTime.of(18, 0), "scheduled", false)));
        when(classes.findAllById(any())).thenReturn(List.of(schoolClass(10L, "Lớp A")));
        when(attendance.countBySessionIds(any())).thenReturn(List.of());

        List<WeeklySessionDto> result = service.week(
                TestUsers.assistant(2L), MONDAY.toString(), SUNDAY.toString());

        assertEquals(List.of(10L), result.stream().map(WeeklySessionDto::classId).toList());
        verify(sessions).findByClassIdInAndSessionDateBetween(List.of(10L), MONDAY, SUNDAY);
    }

    private static ClassSession session(long id, long classId, LocalDate date, LocalTime start,
                                        String status, boolean makeup) {
        ClassSession value = new ClassSession();
        ReflectionTestUtils.setField(value, "id", id);
        value.setClassId(classId);
        value.setSessionDate(date);
        value.setStartTime(start);
        value.setEndTime(start.plusHours(2));
        value.setStatus(status);
        value.setMakeup(makeup);
        return value;
    }

    private static SchoolClass schoolClass(long id, String name) {
        SchoolClass value = new SchoolClass();
        ReflectionTestUtils.setField(value, "id", id);
        value.setName(name);
        return value;
    }
}
