package com.classmanagement.audit;

import com.classmanagement.audit.dto.AuditLogDto;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.classmanagement.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository, new ObjectMapper());
    }

    @Test
    void returnsNewestFirstPageWithParsedSnapshotsAndActor() {
        AuditLog log = auditLog(9L, "fees", "create", "{\"amount\":500000}", actor());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        PageResponse<AuditLogDto> result = service.list(
                Pagination.of("1", "20"),
                new AuditLogFilter("fees", "admin@", "create", null, null, "payment"));

        assertEquals(1, result.total());
        assertEquals("admin@test.local", result.items().getFirst().actor().email());
        assertEquals(500000, result.items().getFirst().newValues().get("amount").asInt());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                pageable.getValue().getSort());
    }

    @Test
    void preservesMalformedLegacySnapshotAsTextNode() {
        AuditLog log = auditLog(9L, "fees", "create", "{broken", null);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        AuditLogDto item = service.list(
                Pagination.of(null, null), AuditLogFilter.empty()).items().getFirst();

        assertTrue(item.newValues().isTextual());
        assertEquals("{broken", item.newValues().textValue());
        assertNull(item.actor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "payments"})
    void rejectsUnknownCategory(String category) {
        ApiException error = assertThrows(ApiException.class, () -> service.list(
                Pagination.of(null, null),
                new AuditLogFilter(category, null, null, null, null, null)));
        assertEquals(400, error.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "login"})
    void rejectsUnknownAction(String action) {
        ApiException error = assertThrows(ApiException.class, () -> service.list(
                Pagination.of(null, null),
                new AuditLogFilter(null, null, action, null, null, null)));
        assertEquals(400, error.getStatus());
    }

    @Test
    void rejectsTimeRangeWhoseStartIsAfterEnd() {
        ApiException error = assertThrows(ApiException.class, () -> service.list(
                Pagination.of(null, null),
                new AuditLogFilter(null, null, null,
                        Instant.parse("2026-07-29T02:00:00Z"),
                        Instant.parse("2026-07-29T01:00:00Z"), null)));
        assertEquals(400, error.getStatus());
    }

    private User actor() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setFullName("Admin");
        user.setEmail("admin@test.local");
        return user;
    }

    private AuditLog auditLog(Long id, String category, String action,
                              String newValues, User actor) {
        AuditLog log = new AuditLog();
        ReflectionTestUtils.setField(log, "id", id);
        ReflectionTestUtils.setField(log, "createdAt",
                Instant.parse("2026-07-29T01:00:00Z"));
        ReflectionTestUtils.setField(log, "actor", actor);
        log.setCategory(category);
        log.setAction(action);
        log.setTableName("payments");
        log.setNewValues(newValues);
        log.setUserId(actor == null ? null : actor.getId());
        return log;
    }
}
