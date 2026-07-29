package com.classmanagement.students;

final class StudentCodes {

    private StudentCodes() {
    }

    static String fromId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Student ID must be positive");
        }
        return "HS%06d".formatted(id);
    }
}
