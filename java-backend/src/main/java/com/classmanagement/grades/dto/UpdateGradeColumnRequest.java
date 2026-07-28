package com.classmanagement.grades.dto;

import java.util.Map;

// Body PUT /api/grade-columns/{id}: giu ngu nghia PATCH nhu UpdateUserRequest —
// phan biet "field vang mat" (bo qua) voi "field: null tuong minh" (vd xoa column_date).
public class UpdateGradeColumnRequest {

    private final Map<String, Object> body;

    public UpdateGradeColumnRequest(Map<String, Object> body) {
        this.body = body;
    }

    /** Field co xuat hien trong JSON khong (ke ca khi gia tri la null). */
    public boolean has(String field) {
        return body.containsKey(field);
    }

    /** Gia tri tho (Number/String/null...) de service tu ep kieu + validate. */
    public Object raw(String field) {
        return body.get(field);
    }

    /** Gia tri dang String; null neu client gui null tuong minh. */
    public String getString(String field) {
        Object v = body.get(field);
        return v == null ? null : String.valueOf(v);
    }
}
