package com.classmanagement.classes.dto;

// Nhan su cua lop: staff: [{user_id, full_name, staff_role}] (main = GV chinh, assistant = tro giang)
public record StaffDto(Long userId, String fullName, String staffRole) {
}
