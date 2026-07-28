package com.classmanagement.classes.dto;

import com.classmanagement.domain.SchoolClass;

import java.math.BigDecimal;
import java.time.LocalDate;

// Item danh sach lop (GET /api/classes) — kem student_count (enrollment dang mo) + main_teacher
public record ClassDto(Long id, String name, Integer grade, String schoolYear,
                       LocalDate startDate, LocalDate endDate, BigDecimal defaultMonthlyFee,
                       String status, String note, long studentCount, MainTeacherDto mainTeacher) {

    public static ClassDto from(SchoolClass c, long studentCount, MainTeacherDto mainTeacher) {
        return new ClassDto(c.getId(), c.getName(), c.getGrade(), c.getSchoolYear(),
                c.getStartDate(), c.getEndDate(), c.getDefaultMonthlyFee(),
                c.getStatus(), c.getNote(), studentCount, mainTeacher);
    }
}
