package com.classmanagement.classes.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body PUT /api/classes/{id}/staff: {main_teacher_id, assistant_ids: [..]} — thay toan bo phan cong
public record ReplaceStaffRequest(
        @NotNull(message = "Thiếu trường bắt buộc: main_teacher_id")
        Long mainTeacherId,

        List<Long> assistantIds) {
}
