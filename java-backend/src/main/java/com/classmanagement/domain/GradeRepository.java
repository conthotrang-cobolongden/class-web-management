package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    // Ma tran diem: lay het diem cua cac cot trong lop (GET /api/classes/{id}/grades)
    List<Grade> findByGradeColumnIdIn(Collection<Long> gradeColumnIds);

    List<Grade> findByGradeColumnId(Long gradeColumnId);

    // Upsert diem: unique (grade_column_id, student_id)
    Optional<Grade> findByGradeColumnIdAndStudentId(Long gradeColumnId, Long studentId);
}
