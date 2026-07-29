package com.classmanagement.audit;

import com.classmanagement.audit.dto.AuditLogDto;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogDto> list(
            @RequestParam(required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(name = "actor_email", required = false) String actorEmail,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(name = "q", required = false) String keyword) {
        AuditLogFilter filter = new AuditLogFilter(
                emptyToNull(category), emptyToNull(actorEmail), emptyToNull(action),
                parseInstant("from", from), parseInstant("to", to), emptyToNull(keyword));
        return service.list(Pagination.of(page, pageSize), filter);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant parseInstant(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new ApiException(400,
                    "Thời gian " + field + " không đúng định dạng ISO-8601");
        }
    }
}