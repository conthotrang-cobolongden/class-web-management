package com.classmanagement.students.dto;

public record StudentImportError(int row, String column, String reason) {
}
