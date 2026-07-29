package com.classmanagement.students.dto;

import com.classmanagement.domain.SchoolClass;
import com.classmanagement.domain.Student;

import java.time.LocalDate;
import java.util.List;

// Ho so chi tiet HS + lich su enrollments (GET /api/students/{id})
public record StudentDetailDto(Long id, String studentCode, String fullName, LocalDate dob, String gender,
                               String schoolName, String parentName, String parentPhone,
                               LocalDate enrollmentDate, String status, String note,
                               StudentDto.CurrentClass currentClass,
                               List<EnrollmentDto> enrollments) {

    public static StudentDetailDto from(Student s, SchoolClass currentClass,
                                        List<EnrollmentDto> enrollments) {
        return new StudentDetailDto(s.getId(), s.getStudentCode(), s.getFullName(), s.getDob(), s.getGender(),
                s.getSchoolName(), s.getParentName(), s.getParentPhone(),
                s.getEnrollmentDate(), s.getStatus(), s.getNote(),
                StudentDto.CurrentClass.from(currentClass), enrollments);
    }
}
