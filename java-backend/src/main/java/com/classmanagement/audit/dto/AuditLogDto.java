package com.classmanagement.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record AuditLogDto(
        Long id, String category, String action, String description,
        String tableName, Long recordId, Instant createdAt,
        AuditActorDto actor, JsonNode oldValues, JsonNode newValues) {
}
