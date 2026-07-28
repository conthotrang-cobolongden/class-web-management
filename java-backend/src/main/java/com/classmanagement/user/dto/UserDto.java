package com.classmanagement.user.dto;

import com.classmanagement.user.User;

import java.time.Instant;

// Cac cot public cua tai khoan (khong bao gio tra password_hash)
public record UserDto(Long id, String fullName, String email, String role,
                      String phone, boolean isActive, Instant createdAt) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getPhone(), u.isActive(), u.getCreatedAt());
    }
}
