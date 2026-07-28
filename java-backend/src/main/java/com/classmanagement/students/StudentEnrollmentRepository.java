package com.classmanagement.students;

import com.classmanagement.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

// Repo BO SUNG cho entity Enrollment, chi phuc vu module students
// (Spring Data cho phep nhieu repository tren 1 entity — khong sua domain.EnrollmentRepository)
public interface StudentEnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // Batch load enrollment mo cua ca trang HS -> build current_class bang 2 query
    List<Enrollment> findByStudentIdInAndLeftAtIsNull(Collection<Long> studentIds);
}
