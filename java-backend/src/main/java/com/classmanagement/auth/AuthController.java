package com.classmanagement.auth;

import com.classmanagement.auth.dto.ChangePasswordRequest;
import com.classmanagement.auth.dto.LoginRequest;
import com.classmanagement.auth.dto.UserSummary;
import com.classmanagement.common.MessageResponse;
import com.classmanagement.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginResponse(String token, UserSummary user) {
    }

    public record MeResponse(UserSummary user) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult result = authService.login(req.email(), req.password());
        return new LoginResponse(result.token(), result.user());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal User me) {
        return new MeResponse(UserSummary.from(me));
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@AuthenticationPrincipal User me,
                                          @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(me.getId(), req.oldPassword(), req.newPassword());
        return new MessageResponse("Đổi mật khẩu thành công");
    }
}
