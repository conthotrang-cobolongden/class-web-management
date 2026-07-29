package com.classmanagement.audit;

import java.time.Instant;

public record AuditLogFilter(
        String category, String actorEmail, String action,
        Instant from, Instant to, String keyword) {
    public static AuditLogFilter empty() {
        return new AuditLogFilter(null, null, null, null, null, null);
    }
}
