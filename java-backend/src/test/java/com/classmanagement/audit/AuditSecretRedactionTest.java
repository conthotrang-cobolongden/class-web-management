package com.classmanagement.audit;

import com.classmanagement.common.AuditService;
import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditSecretRedactionTest {

    @Test
    void recursivelyRemovesPasswordsHashesTokensAndSecrets() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditService service = new AuditService(repository, new ObjectMapper());

        service.audit("users", "users", 4L, "update", "Cập nhật tài khoản",
                Map.of("email", "safe@test.local", "password_hash", "old-hash"),
                Map.of("profile", Map.of("token", "jwt", "name", "An"),
                        "password", "plain", "secret", "hidden", "authorization_header", "Bearer credential"),
                1L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        String json = String.valueOf(captor.getValue().getOldValues())
                + String.valueOf(captor.getValue().getNewValues());
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("token"));
        assertFalse(json.toLowerCase().contains("secret"));
        assertFalse(json.contains("old-hash"));
        assertFalse(json.contains("plain"));
        assertFalse(json.contains("jwt"));
        assertFalse(json.contains("hidden"));
        assertFalse(json.contains("Bearer credential"));
    }
}
