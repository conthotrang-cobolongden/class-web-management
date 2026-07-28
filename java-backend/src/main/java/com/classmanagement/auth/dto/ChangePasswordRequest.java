package com.classmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Thiếu trường bắt buộc: old_password")
        String oldPassword,

        @NotBlank(message = "Thiếu trường bắt buộc: new_password")
        @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
        String newPassword) {
}
