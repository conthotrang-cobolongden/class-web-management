package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassStaffRepository extends JpaRepository<ClassStaff, Long> {

    List<ClassStaff> findByClassId(Long classId);

    List<ClassStaff> findByClassIdIn(Collection<Long> classIds);

    List<ClassStaff> findByUserId(Long userId);

    // GV chinh cua cac lop nao — phuc vu bao cao doanh thu (FR-6.5)
    List<ClassStaff> findByUserIdAndStaffRole(Long userId, String staffRole);

    Optional<ClassStaff> findByClassIdAndUserId(Long classId, Long userId);

    // staffRole = "main" -> GV chinh cua lop (toi da 1 theo unique index)
    Optional<ClassStaff> findByClassIdAndStaffRole(Long classId, String staffRole);

    boolean existsByClassIdAndUserId(Long classId, Long userId);

    // PUT /api/classes/{id}/staff — thay toan bo phan cong (goi trong @Transactional)
    void deleteByClassId(Long classId);
}
