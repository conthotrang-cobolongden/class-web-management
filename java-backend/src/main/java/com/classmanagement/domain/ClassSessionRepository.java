package com.classmanagement.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByClassIdAndSessionDateBetween(Long classId, LocalDate from, LocalDate to);

    // FR-4.4: cac buoi scheduled trong range de tinh chuyen can
    List<ClassSession> findByClassIdAndStatusAndSessionDateBetween(Long classId, String status,
                                                                   LocalDate from, LocalDate to);

    List<ClassSession> findByClassIdOrderBySessionDateAscStartTimeAsc(Long classId);

    // generate-sessions idempotent: unique (class_id, session_date, start_time)
    boolean existsByClassIdAndSessionDateAndStartTime(Long classId, LocalDate sessionDate, LocalTime startTime);

    // GET /api/sessions — classId null chi danh cho admin (service tu chan)
    @Query("""
            select s from ClassSession s
            where (:classId is null or s.classId = :classId)
              and (:from is null or s.sessionDate >= :from)
              and (:to is null or s.sessionDate <= :to)
              and (:status is null or s.status = :status)
            """)
    Page<ClassSession> search(@Param("classId") Long classId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("status") String status,
                              Pageable pageable);
}
