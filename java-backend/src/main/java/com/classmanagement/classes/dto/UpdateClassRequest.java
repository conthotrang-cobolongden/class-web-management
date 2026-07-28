package com.classmanagement.classes.dto;

import com.classmanagement.common.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

// Body PUT /api/classes/{id}: giu ngu nghia PATCH nhu PUT users — phan biet
// "field vang mat" (bo qua) voi "field: null tuong minh" (ghi de null).
// Nhan Map tho vi record + Jackson khong phan biet duoc 2 truong hop do.
public class UpdateClassRequest {

    private final Map<String, Object> body;

    public UpdateClassRequest(Map<String, Object> body) {
        this.body = body;
    }

    /** Field co xuat hien trong JSON khong (ke ca khi gia tri la null). */
    public boolean has(String field) {
        return body.containsKey(field);
    }

    /** Gia tri dang String; null neu client gui null tuong minh. */
    public String getString(String field) {
        Object v = body.get(field);
        return v == null ? null : String.valueOf(v);
    }

    /** Gia tri dang Integer; 400 neu khong phai so nguyen. */
    public Integer getInt(String field) {
        Object v = body.get(field);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Trường " + field + " phải là số nguyên");
        }
    }

    /** Gia tri tien (VND, BigDecimal); 400 neu khong phai so. */
    public BigDecimal getMoney(String field) {
        Object v = body.get(field);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Trường " + field + " phải là số");
        }
    }

    /** Gia tri ngay YYYY-MM-DD; 400 neu sai dinh dang. */
    public LocalDate getDate(String field) {
        Object v = body.get(field);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Trường " + field + " không hợp lệ (định dạng YYYY-MM-DD)");
        }
    }
}
