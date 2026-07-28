package com.classmanagement.students.dto;

import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.Student;

import java.time.LocalDate;

// Item danh sach hoc sinh (GET /api/students) — kem lop dang hoc current_class (FR-3.3)
public record StudentDto(Long id, String fullName, LocalDate dob, String gender,
                         String schoolName, String parentName, String parentPhone,
                         LocalDate enrollmentDate, String status, String note,
                         CurrentClass currentClass) {

    // {id, name} cua lop dang hoc; null = chua/khong co lop
    public record CurrentClass(Long id, String name) {

        public static CurrentClass from(SchoolClass c) {
            return c == null ? null : new CurrentClass(c.getId(), c.getName());
        }
    }

    public static StudentDto from(Student s, SchoolClass currentClass) {
        return new StudentDto(s.getId(), s.getFullName(), s.getDob(), s.getGender(),
                s.getSchoolName(), s.getParentName(), s.getParentPhone(),
                s.getEnrollmentDate(), s.getStatus(), s.getNote(),
                CurrentClass.from(currentClass));
    }
}
