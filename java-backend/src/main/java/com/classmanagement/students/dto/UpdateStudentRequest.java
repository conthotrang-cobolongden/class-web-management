package com.classmanagement.students.dto;

import com.classmanagement.common.ApiException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

// Body PUT /api/students/{id}: giu ngu nghia PATCH nhu UpdateUserRequest —
// phan biet "field vang mat" (bo qua) voi "field: null tuong minh" (ghi de null).
public class UpdateStudentRequest {

    private final Map<String, Object> body;

    public UpdateStudentRequest(Map<String, Object> body) {
        this.body = body;
    }

    /** Field co xuat hien trong JSON khong (ke ca khi gia tri la null). */
    public boolean has(String field) {
        return body.containsKey(field);
    }

    /** Gia tri dang String; null neu client gui null tuong minh. */
    public String get(String field) {
        Object v = body.get(field);
        return v == null ? null : String.valueOf(v);
    }

    /** Gia tri dang LocalDate (YYYY-MM-DD); null neu client gui null tuong minh. */
    public LocalDate getDate(String field) {
        String v = get(field);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " phải có định dạng YYYY-MM-DD");
        }
    }
}
