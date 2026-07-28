package com.classmanagement.reports;

import com.classmanagement.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// Repo BO SUNG cho bao cao (Spring Data cho phep nhieu repo tren 1 entity) —
// khong sua duoc ClassSessionRepository trong package domain.
public interface ReportSessionRepository extends JpaRepository<ClassSession, Long> {

    // FR-7.1: cac buoi scheduled cua lop trong range (from/to nullable = khong gioi han).
    // "Da qua" hay chua loc tiep o service theo gio bat dau VN (TimeUtil).
    @Query("""
            select s from ClassSession s
            where s.classId = :classId
              and s.status = 'scheduled'
              and (:from is null or s.sessionDate >= :from)
              and (:to is null or s.sessionDate <= :to)
            order by s.sessionDate asc, s.startTime asc
            """)
    List<ClassSession> findScheduledInRange(@Param("classId") Long classId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}
