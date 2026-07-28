package com.classmanagement.grades.dto;

import java.math.BigDecimal;
import java.util.List;

// Body PUT /api/grades — bulk upsert diem ca lop tren 1 man hinh (FR-5.2)
public record SaveGradesRequest(List<Item> items) {

    public record Item(Long gradeColumnId, Long studentId, BigDecimal score) {
    }
}
