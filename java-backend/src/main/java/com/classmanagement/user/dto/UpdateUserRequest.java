package com.classmanagement.user.dto;

import java.util.Map;

// Body PUT /api/users/{id}: giu ngu nghia PATCH cua ban Node — phan biet
// "field vang mat" (bo qua) voi "field: null tuong minh" (ghi de null).
// Record + Jackson khong phan biet duoc 2 truong hop do nen nhan Map tho.
public class UpdateUserRequest {

    private final Map<String, Object> body;

    public UpdateUserRequest(Map<String, Object> body) {
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
}
