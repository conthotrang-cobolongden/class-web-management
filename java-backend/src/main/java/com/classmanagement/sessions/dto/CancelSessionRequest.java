package com.classmanagement.sessions.dto;

import jakarta.validation.constraints.NotBlank;

// Body POST /api/sessions/{id}/cancel — ly do bat buoc (FR-2.4)
public record CancelSessionRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: cancel_reason")
        String cancelReason) {
}
