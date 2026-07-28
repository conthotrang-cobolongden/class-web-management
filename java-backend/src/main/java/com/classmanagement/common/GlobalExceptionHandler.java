package com.classmanagement.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String error) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> apiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Dữ liệu không hợp lệ");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Trường " + ex.getName() + " phải là số nguyên"));
    }

    // Sai/thieu Content-Type cung tra 400 nhu ban Node (body-parser bo qua -> loi validate 400)
    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> unreadable(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Dữ liệu gửi lên không hợp lệ (JSON sai định dạng)"));
    }

    // Express khong co 405: sai method roi xuong notFoundHandler -> tra 404 giong het de giu contract
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception ex) {
        return ResponseEntity.status(404).body(new ErrorResponse("Không tìm thấy đường dẫn API"));
    }

    // Vi pham rang buoc DB: unique -> 409 (nhu ban Node), con lai coi la loi he thong
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex) {
        String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (msg.matches("(?is).*(unique|duplicate|uq_|ux_).*")) {
            return ResponseEntity.status(409)
                    .body(new ErrorResponse("Dữ liệu bị trùng (vi phạm ràng buộc duy nhất)"));
        }
        log.error("[ERROR] Vi pham rang buoc du lieu", ex);
        return ResponseEntity.status(500).body(new ErrorResponse("Lỗi hệ thống, vui lòng thử lại"));
    }

    // De 2 loai nay cho tang security xu ly dung 401/403 thay vi roi vao handler Exception
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurity(Exception ex) throws Exception {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("[ERROR]", ex);
        return ResponseEntity.status(500).body(new ErrorResponse("Lỗi hệ thống, vui lòng thử lại"));
    }
}
