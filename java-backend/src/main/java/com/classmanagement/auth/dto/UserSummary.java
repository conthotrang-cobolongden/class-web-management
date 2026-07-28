package com.classmanagement.auth.dto;

import com.classmanagement.user.User;

// Thong tin tai khoan tra ve khi login / GET /me
public record UserSummary(Long id, String fullName, String email, String role) {

    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole());
    }
}
