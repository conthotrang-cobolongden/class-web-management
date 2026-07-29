package com.classmanagement.audit;

import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.classmanagement.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditLogSpecificationsTest {

    @Autowired AuditLogRepository repository;
    @Autowired TestEntityManager entities;

    @Test
    void combinesActorEmailCategoryActionKeywordAndInclusiveTime() {
        User actor = persistUser("Admin One", "ADMIN@Test.Local");
        persistLog("fees", "create", "Ghi nhận payment", "payments", actor,
                Instant.parse("2026-07-29T01:00:00Z"));
        persistLog("students", "update", "Cập nhật hồ sơ", "students", null,
                Instant.parse("2026-07-29T02:00:00Z"));
        entities.clear();

        AuditLogFilter filter = new AuditLogFilter(
                "fees", "admin@test", "create",
                Instant.parse("2026-07-29T01:00:00Z"),
                Instant.parse("2026-07-29T01:00:00Z"), "PAYMENT");

        List<AuditLog> result = repository.findAll(AuditLogSpecifications.from(filter));

        assertEquals(1, result.size());
        assertEquals("ADMIN@Test.Local", result.getFirst().getActor().getEmail());
    }

    private User persistUser(String name, String email) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash("bcrypt-test-hash");
        user.setRole("admin");
        return entities.persistAndFlush(user);
    }

    private AuditLog persistLog(String category, String action, String description,
                                String tableName, User actor, Instant createdAt) {
        AuditLog log = new AuditLog();
        log.setCategory(category);
        log.setAction(action);
        log.setDescription(description);
        log.setTableName(tableName);
        log.setUserId(actor == null ? null : actor.getId());
        ReflectionTestUtils.setField(log, "createdAt", createdAt);
        return entities.persistAndFlush(log);
    }
}
