package com.classmanagement.sessions;

import com.classmanagement.common.MessageResponse;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.sessions.dto.CancelSessionRequest;
import com.classmanagement.sessions.dto.CreateSessionRequest;
import com.classmanagement.sessions.dto.SessionDto;
import com.classmanagement.sessions.dto.UpdateSessionRequest;
import com.classmanagement.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Sessions — API.md muc 3; quyen chan trong service qua ClassAccessService (NFR-2)
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public record SessionResponse(SessionDto session) {
    }

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public PageResponse<SessionDto> list(@RequestParam(name = "class_id", required = false) Long classId,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String page,
                                         @RequestParam(name = "page_size", required = false) String pageSize,
                                         @AuthenticationPrincipal User me) {
        return sessionService.list(me, classId, from, to, status, Pagination.of(page, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest req,
                                  @AuthenticationPrincipal User me) {
        return new SessionResponse(sessionService.create(me, req));
    }

    @PutMapping("/{id}")
    public SessionResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal User me) {
        return new SessionResponse(sessionService.update(me, id, new UpdateSessionRequest(body)));
    }

    @PostMapping("/{id}/cancel")
    public MessageResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelSessionRequest req,
                                  @AuthenticationPrincipal User me) {
        sessionService.cancel(me, id, req);
        return new MessageResponse("Đã hủy buổi học");
    }
}
