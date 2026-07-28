package com.classmanagement.sessions.dto;

import java.util.Map;

// Body PUT /api/sessions/{id}: giu ngu nghia PATCH nhu UpdateUserRequest ben user —
// phan biet "field vang mat" (bo qua) voi "field: null tuong minh".
public class UpdateSessionRequest {

    private final Map<String, Object> body;

    public UpdateSessionRequest(Map<String, Object> body) {
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
