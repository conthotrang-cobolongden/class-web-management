package com.classmanagement.audit;

import com.classmanagement.domain.AuditLog;
import com.classmanagement.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> from(AuditLogFilter filter) {
        return Specification.allOf(
                equal("category", filter.category()),
                actorEmailContains(filter.actorEmail()),
                equal("action", filter.action()),
                atOrAfter(filter.from()),
                atOrBefore(filter.to()),
                keywordContains(filter.keyword()));
    }

    private static Specification<AuditLog> actorEmailContains(String email) {
        return (root, query, cb) -> {
            if (!hasText(email)) {
                return cb.conjunction();
            }
            Join<AuditLog, User> actor = root.join("actor", JoinType.LEFT);
            return cb.like(cb.lower(actor.get("email")),
                    "%" + email.trim().toLowerCase(Locale.ROOT) + "%");
        };
    }

    private static Specification<AuditLog> keywordContains(String keyword) {
        return (root, query, cb) -> {
            if (!hasText(keyword)) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("tableName")), pattern),
                    cb.like(cb.lower(root.get("action")), pattern));
        };
    }

    private static Specification<AuditLog> equal(String field, String value) {
        return (root, query, cb) -> hasText(value)
                ? cb.equal(root.get(field), value.trim())
                : cb.conjunction();
    }

    private static Specification<AuditLog> atOrAfter(Instant value) {
        return (root, query, cb) -> value == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("createdAt"), value);
    }

    private static Specification<AuditLog> atOrBefore(Instant value) {
        return (root, query, cb) -> value == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("createdAt"), value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
