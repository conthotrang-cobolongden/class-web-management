package com.classmanagement.audit;

import com.classmanagement.audit.dto.AuditActorDto;
import com.classmanagement.audit.dto.AuditLogDto;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuditLogService {

    private static final Set<String> CATEGORIES = Set.of(
            "authentication", "students", "classes", "sessions", "attendance",
            "grades", "fees", "users", "imports");
    private static final Set<String> ACTIONS = Set.of(
            "create", "update", "delete", "login_success", "login_failure", "import");
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> list(Pagination pagination, AuditLogFilter filter) {
        validate(filter);
        Pageable pageable = PageRequest.of(
                pagination.page() - 1, pagination.pageSize(), NEWEST_FIRST);
        Page<AuditLog> result = repository.findAll(
                AuditLogSpecifications.from(filter), pageable);
        return new PageResponse<>(
                result.getContent().stream().map(this::toDto).toList(),
                result.getTotalElements(), pagination.page(), pagination.pageSize());
    }

    private void validate(AuditLogFilter filter) {
        if (filter.category() != null && !CATEGORIES.contains(filter.category())) {
            throw new ApiException(400, "Category audit không hợp lệ");
        }
        if (filter.action() != null && !ACTIONS.contains(filter.action())) {
            throw new ApiException(400, "Action audit không hợp lệ");
        }
        if (filter.from() != null && filter.to() != null
                && filter.from().isAfter(filter.to())) {
            throw new ApiException(400,
                    "Thời gian bắt đầu phải trước thời gian kết thúc");
        }
    }

    private AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(
                log.getId(), log.getCategory(), log.getAction(), log.getDescription(),
                log.getTableName(), log.getRecordId(), log.getCreatedAt(),
                AuditActorDto.from(log.getActor()),
                snapshot(log.getOldValues()), snapshot(log.getNewValues()));
    }

    private JsonNode snapshot(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ignored) {
            return TextNode.valueOf(json);
        }
    }
}
