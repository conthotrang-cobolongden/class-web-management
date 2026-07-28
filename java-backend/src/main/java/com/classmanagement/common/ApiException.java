package com.classmanagement.common;

// Loi nghiep vu co status HTTP kem message tieng Viet — tuong duong ApiError ben Node
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
