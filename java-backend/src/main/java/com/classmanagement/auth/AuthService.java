package com.classmanagement.auth;

import com.classmanagement.auth.dto.UserSummary;
import com.classmanagement.common.ApiException;
import com.classmanagement.security.JwtService;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import com.classmanagement.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record LoginResult(String token, UserSummary user) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    // FR-1.1: dang nhap email + mat khau -> JWT
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(UserService.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isActive()) {
            throw new ApiException(401, "Email hoặc mật khẩu không đúng");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(401, "Email hoặc mật khẩu không đúng");
        }
        return new LoginResult(jwtService.issueToken(user), UserSummary.from(user));
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
    }
}
