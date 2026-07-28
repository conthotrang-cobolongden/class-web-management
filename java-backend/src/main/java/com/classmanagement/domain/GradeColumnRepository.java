package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeColumnRepository extends JpaRepository<GradeColumn, Long> {

    // Cot diem con hieu luc cua lop (bo cot da soft delete)
    List<GradeColumn> findByClassIdAndIsDeletedFalseOrderByIdAsc(Long classId);

    Optional<GradeColumn> findByIdAndIsDeletedFalse(Long id);
}
