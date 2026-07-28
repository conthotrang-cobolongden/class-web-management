package com.classmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: email")
        String email,

        @NotBlank(message = "Thiếu trường bắt buộc: password")
        String password) {
}
