package com.classmanagement.auth;

import com.classmanagement.auth.dto.UserSummary;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.security.JwtService;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import com.classmanagement.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {

    public record LoginResult(String token, UserSummary user) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtService jwtService, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    // FR-1.1: dang nhap email + mat khau -> JWT
    public LoginResult login(String email, String password) {
        String normalizedEmail = UserService.normalizeEmail(email);
        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !user.isActive()) {
            audit.authenticationEvent(
                    "login_failure", normalizedEmail, user == null ? null : user.getId(),
                    "Đăng nhập thất bại");
            throw new ApiException(401, "Email hoặc mật khẩu không đúng");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            audit.authenticationEvent(
                    "login_failure", normalizedEmail, user.getId(), "Đăng nhập thất bại");
            throw new ApiException(401, "Email hoặc mật khẩu không đúng");
        }
        String token = jwtService.issueToken(user);
        audit.authenticationEvent(
                "login_success", normalizedEmail, user.getId(), "Đăng nhập thành công");
        return new LoginResult(token, UserSummary.from(user));
    }

    // FR-1.2: user tu doi mat khau
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));
        if (!encoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ApiException(400, "Mật khẩu cũ không đúng");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        audit.audit("authentication", "users", userId, "update",
                "Người dùng tự đổi mật khẩu", null,
                Map.of("password_changed", true), userId);
    }
}
