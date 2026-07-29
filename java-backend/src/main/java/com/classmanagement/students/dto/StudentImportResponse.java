package com.classmanagement.students.dto;

import java.util.List;

public record StudentImportResponse(int imported, List<String> studentCodes,
                                    List<StudentImportError> errors) {

    public StudentImportResponse {
        studentCodes = studentCodes == null ? List.of() : List.copyOf(studentCodes);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static StudentImportResponse invalid(List<StudentImportError> errors) {
        return new StudentImportResponse(0, List.of(), errors);
    }
}
