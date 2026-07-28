package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySessionId(Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    long countBySessionId(Long sessionId);

    // attendance_count cho danh sach buoi hoc — [sessionId, count]
    @Query("""
            select a.sessionId, count(a.id) from AttendanceRecord a
            where a.sessionId in :sessionIds
            group by a.sessionId
            """)
    List<Object[]> countBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    // FR-4.4 thong ke chuyen can — [studentId, status, count] tren cac buoi da chon
    @Query("""
            select a.studentId, a.status, count(a.id) from AttendanceRecord a
            where a.sessionId in :sessionIds
            group by a.studentId, a.status
            """)
    List<Object[]> countByStudentAndStatus(@Param("sessionIds") Collection<Long> sessionIds);
}
