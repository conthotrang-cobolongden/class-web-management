package com.classmanagement.user;

import com.classmanagement.common.MessageResponse;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.user.dto.CreateUserRequest;
import com.classmanagement.user.dto.UpdateUserRequest;
import com.classmanagement.user.dto.UserDto;
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

// Chi admin vao duoc (chan o SecurityConfig: /api/users/** -> hasAuthority("admin"))
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserResponse(UserDto user) {
    }

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(required = false) String page,
                                      @RequestParam(name = "page_size", required = false) String pageSize,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) String active) {
        return userService.list(Pagination.of(page, pageSize), role, active);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return new UserResponse(userService.create(req));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                               @AuthenticationPrincipal User me) {
        return new UserResponse(userService.update(id, new UpdateUserRequest(body), me.getId()));
    }

    @PostMapping("/{id}/deactivate")
    public MessageResponse deactivate(@PathVariable Long id, @AuthenticationPrincipal User me) {
        userService.deactivate(id, me.getId());
        return new MessageResponse("Đã vô hiệu hóa tài khoản");
    }

    @PostMapping("/{id}/activate")
    public MessageResponse activate(@PathVariable Long id) {
        userService.activate(id);
        return new MessageResponse("Đã kích hoạt lại tài khoản");
    }
}
