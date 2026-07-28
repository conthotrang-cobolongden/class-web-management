package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // Enrollment dang mo cua HS (toi da 1 theo unique index ux_enrollments_active)
    Optional<Enrollment> findByStudentIdAndLeftAtIsNull(Long studentId);

    boolean existsByStudentIdAndLeftAtIsNull(Long studentId);

    // HS dang hoc mot lop
    List<Enrollment> findByClassIdAndLeftAtIsNull(Long classId);

    long countByClassIdAndLeftAtIsNull(Long classId);

    // Lich su lop cua HS (GET /api/students/{id})
    List<Enrollment> findByStudentIdOrderByJoinedAtAscIdAsc(Long studentId);

    // Check quyen xem ho so: HS dang/tung hoc lop cua staff
    boolean existsByStudentIdAndClassIdIn(Long studentId, Collection<Long> classIds);

    // FR-6.2: sinh phi thang cho moi enrollment dang mo
    List<Enrollment> findByLeftAtIsNull();

    // Danh sach diem danh: HS dang hoc lop TAI ngay buoi hoc (GET /api/sessions/{id}/attendance)
    @Query("""
            select e from Enrollment e
            where e.classId = :classId
              and e.joinedAt <= :date
              and (e.leftAt is null or e.leftAt > :date)
            """)
    List<Enrollment> findOpenByClassAtDate(@Param("classId") Long classId, @Param("date") LocalDate date);

    // student_count cho danh sach lop — [classId, count]
    @Query("""
            select e.classId, count(e.id) from Enrollment e
            where e.leftAt is null and e.classId in :classIds
            group by e.classId
            """)
    List<Object[]> countOpenByClassIds(@Param("classIds") Collection<Long> classIds);
}
