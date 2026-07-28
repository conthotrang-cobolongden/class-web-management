package com.classmanagement.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findByStatus(String status);

    boolean existsByNameAndSchoolYear(String name, String schoolYear);

    boolean existsByNameAndSchoolYearAndIdNot(String name, String schoolYear, Long id);

    // GET /api/classes — admin: loc tren tat ca lop
    @Query("""
            select c from SchoolClass c
            where (:grade is null or c.grade = :grade)
              and (:status is null or c.status = :status)
              and (:schoolYear is null or c.schoolYear = :schoolYear)
            """)
    Page<SchoolClass> search(@Param("grade") Integer grade,
                             @Param("status") String status,
                             @Param("schoolYear") String schoolYear,
                             Pageable pageable);

    // GET /api/classes — GV/TG: chi loc trong cac lop minh duoc gan (visibleClassIds)
    @Query("""
            select c from SchoolClass c
            where c.id in :ids
              and (:grade is null or c.grade = :grade)
              and (:status is null or c.status = :status)
              and (:schoolYear is null or c.schoolYear = :schoolYear)
            """)
    Page<SchoolClass> searchWithin(@Param("ids") Collection<Long> ids,
                                   @Param("grade") Integer grade,
                                   @Param("status") String status,
                                   @Param("schoolYear") String schoolYear,
                                   Pageable pageable);

    // FR-7.4 dashboard: so lop active theo khoi — [grade, count]
    @Query("""
            select c.grade, count(c.id) from SchoolClass c
            where c.status = 'active'
            group by c.grade
            """)
    List<Object[]> countActiveByGrade();
}
