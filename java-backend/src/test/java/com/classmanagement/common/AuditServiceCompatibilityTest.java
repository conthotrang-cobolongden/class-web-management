package com.classmanagement.common;

import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceCompatibilityTest {

    @Test
    void assignsCategoryForExistingAuditCallSignature() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditService service = new AuditService(repository, new ObjectMapper());

        service.audit("grade_columns", 12L, "update", Map.of("name", "Cũ"),
                Map.of("name", "Mới"), 4L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals("grades", captor.getValue().getCategory());
    }
}
