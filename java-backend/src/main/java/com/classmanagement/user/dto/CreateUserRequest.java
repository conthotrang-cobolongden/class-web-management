package com.classmanagement.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Khong validate dinh dang email — ban Node chi check required, giu nguyen contract
public record CreateUserRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: full_name")
        String fullName,

        @NotBlank(message = "Thiếu trường bắt buộc: email")
        String email,

        @NotBlank(message = "Thiếu trường bắt buộc: password")
        @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
        String password,

        @NotBlank(message = "Thiếu trường bắt buộc: role")
        String role,

        String phone) {
}
