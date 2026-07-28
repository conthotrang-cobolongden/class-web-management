package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTableNameAndRecordIdOrderByIdDesc(String tableName, Long recordId);
}
