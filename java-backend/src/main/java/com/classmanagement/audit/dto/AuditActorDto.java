package com.classmanagement.audit.dto;

import com.classmanagement.user.User;

public record AuditActorDto(Long id, String fullName, String email) {
    public static AuditActorDto from(User user) {
        return user == null ? null : new AuditActorDto(
                user.getId(), user.getFullName(), user.getEmail());
    }
}
