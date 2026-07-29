package com.classmanagement.common;

import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

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
        audit(categoryFor(tableName), tableName, recordId, action,
                tableName + (recordId == null ? "" : " #" + recordId),
                oldValues, newValues, userId);
    }

    @Transactional
    public void audit(String category, String tableName, Long recordId, String action,
                      String description, Object oldValues, Object newValues, Long userId) {
        AuditLog log = new AuditLog();
        log.setTableName(tableName);
        log.setCategory(category);
        log.setDescription(limit(description, 300));
        log.setRecordId(recordId);
        log.setAction(action);
        log.setOldValues(toJson(oldValues));
        log.setNewValues(toJson(newValues));
        log.setUserId(userId);
        auditLogs.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void authenticationEvent(String action, String submittedEmail,
                                    Long resolvedUserId, String description) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("email", submittedEmail);
        metadata.put("resolved_user_id", resolvedUserId);
        audit("authentication", "authentication", null, action, description,
                null, metadata, resolvedUserId);
    }

    private static String limit(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
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
            JsonNode tree = objectMapper.valueToTree(value);
            scrubSecrets(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được dữ liệu audit", e);
        }
    }
    private static void scrubSecrets(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ArrayList<String> remove = new ArrayList<>();
            object.fieldNames().forEachRemaining(name -> {
                if (isSensitive(name)) {
                    remove.add(name);
                } else {
                    scrubSecrets(object.get(name));
                }
            });
            remove.forEach(object::remove);
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(AuditService::scrubSecrets);
        }
    }

    private static boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("hash")
                || normalized.contains("authorization");
    }
}
