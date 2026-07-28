package com.classmanagement.grades.dto;

import com.classmanagement.domain.GradeColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

// Cot diem tra ve client (bo is_deleted, created_by... — API.md muc 5)
public record GradeColumnDto(Long id, String name, BigDecimal weight,
                             BigDecimal maxScore, LocalDate columnDate) {

    public static GradeColumnDto from(GradeColumn c) {
        return new GradeColumnDto(c.getId(), c.getName(), c.getWeight(),
                c.getMaxScore(), c.getColumnDate());
    }
}
