package com.classmanagement.grades.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// GET /api/classes/{id}/grades — ma tran HS dang hoc x cot diem (FR-5.2, FR-5.3).
// scores: key = id cot (dang chuoi), value = diem hoac null neu chua nhap.
// average: diem TB co trong so lam tron 2 so le, null neu chua co diem nao.
public record GradeMatrixResponse(List<GradeColumnDto> columns, List<StudentRow> items) {

    public record StudentRow(Long studentId, String fullName,
                             Map<String, BigDecimal> scores, BigDecimal average) {
    }
}
