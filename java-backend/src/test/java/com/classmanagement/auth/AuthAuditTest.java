package com.classmanagement.auth;

import com.classmanagement.TestUsers;
import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.security.JwtService;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAuditTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuditService audit;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        audit = mock(AuditService.class);
        service = new AuthService(users, encoder, jwt, audit);
    }

    @Test
    void auditsSuccessfulLoginWithoutPassingPasswordOrToken() {
        User user = TestUsers.teacher(12L);
        when(users.findByEmail("teacher@test.local")).thenReturn(Optional.of(user));
        when(encoder.matches("Secret123", user.getPasswordHash())).thenReturn(true);
        when(jwt.issueToken(user)).thenReturn("issued.jwt.token");

        AuthService.LoginResult result = service.login(" Teacher@Test.Local ", "Secret123");

        assertEquals("issued.jwt.token", result.token());
        verify(audit).authenticationEvent(
                "login_success", "teacher@test.local", 12L, "Đăng nhập thành công");
    }

    @Test
    void auditsFailedLoginForUnknownEmail() {
        when(users.findByEmail("missing@test.local")).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> service.login(" Missing@Test.Local ", "NeverLogThis"));

        assertEquals(401, error.getStatus());
        verify(audit).authenticationEvent(
                "login_failure", "missing@test.local", null, "Đăng nhập thất bại");
    }

    @Test
    void passwordChangeAuditContainsOnlySafeMetadata() {
        User user = TestUsers.assistant(9L);
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(encoder.matches("OldSecret", user.getPasswordHash())).thenReturn(true);
        when(encoder.encode("NewSecret")).thenReturn("bcrypt-hash");

        service.changePassword(9L, "OldSecret", "NewSecret");

        verify(audit).audit(
                org.mockito.ArgumentMatchers.eq("authentication"),
                org.mockito.ArgumentMatchers.eq("users"),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("update"),
                contains("đổi mật khẩu"),
                org.mockito.ArgumentMatchers.isNull(),
                any(),
                org.mockito.ArgumentMatchers.eq(9L));
    }
}
