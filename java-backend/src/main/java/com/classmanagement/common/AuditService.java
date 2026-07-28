package com.classmanagement.common;

import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ghi audit log cho thao tac nhay cam (NFR-4) — port tu backend/src/services/audit.js.
// oldValues/newValues nhan Object bat ky (Map, DTO...) -> serialize JSON bang ObjectMapper.
@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void audit(String tableName, Long recordId, String action,
                      Object oldValues, Object newValues, Long userId) {
        AuditLog log = new AuditLog();
        log.setTableName(tableName);
        log.setCategory(categoryFor(tableName));
        log.setDescription(tableName + (recordId == null ? "" : " #" + recordId));
        log.setRecordId(recordId);
        log.setAction(action);
        log.setOldValues(toJson(oldValues));
        log.setNewValues(toJson(newValues));
        log.setUserId(userId);
        auditLogs.save(log);
    }

    private static String categoryFor(String tableName) {
        return switch (tableName) {
            case "users" -> "users";
            case "students", "enrollments" -> "students";
            case "classes", "class_staff" -> "classes";
            case "class_schedules", "sessions" -> "sessions";
            case "attendance" -> "attendance";
            case "grade_columns", "grades" -> "grades";
            case "monthly_fees", "payments" -> "fees";
            case "imports" -> "imports";
            case "authentication" -> "authentication";
            default -> throw new IllegalArgumentException(
                    "Khong co nhom audit cho bang: " + tableName);
        };
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được dữ liệu audit", e);
        }
    }
}
