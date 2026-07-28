package com.classmanagement.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // GET /api/students — admin: tim tren toan bo HS (FR-3.3)
    @Query("""
            select s from Student s
            where (:q is null or lower(s.fullName) like lower(concat('%', :q, '%')) or lower(s.studentCode) = lower(:q))
              and (:status is null or s.status = :status)
              and (:classId is null or exists (
                    select 1 from Enrollment e
                    where e.studentId = s.id and e.leftAt is null and e.classId = :classId))
              and (:grade is null or exists (
                    select 1 from Enrollment e2 join SchoolClass c on c.id = e2.classId
                    where e2.studentId = s.id and e2.leftAt is null and c.grade = :grade))
            """)
    Page<Student> search(@Param("q") String q,
                         @Param("status") String status,
                         @Param("classId") Long classId,
                         @Param("grade") Integer grade,
                         Pageable pageable);

    // GET /api/students — GV/TG: chi HS dang hoc cac lop minh (visibleClassIds)
    @Query("""
            select s from Student s
            where exists (
                    select 1 from Enrollment ev
                    where ev.studentId = s.id and ev.leftAt is null and ev.classId in :classIds)
              and (:q is null or lower(s.fullName) like lower(concat('%', :q, '%')) or lower(s.studentCode) = lower(:q))
              and (:status is null or s.status = :status)
              and (:classId is null or exists (
                    select 1 from Enrollment e
                    where e.studentId = s.id and e.leftAt is null and e.classId = :classId))
              and (:grade is null or exists (
                    select 1 from Enrollment e2 join SchoolClass c on c.id = e2.classId
                    where e2.studentId = s.id and e2.leftAt is null and c.grade = :grade))
            """)
    Page<Student> searchWithin(@Param("classIds") Collection<Long> classIds,
                               @Param("q") String q,
                               @Param("status") String status,
                               @Param("classId") Long classId,
                               @Param("grade") Integer grade,
                               Pageable pageable);

    // FR-7.4 dashboard
    long countByStatus(String status);


    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);
    long countByEnrollmentDateBetween(LocalDate from, LocalDate to);
}
