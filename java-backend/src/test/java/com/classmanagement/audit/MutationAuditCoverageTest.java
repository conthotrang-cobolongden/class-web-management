package com.classmanagement.audit;

import com.classmanagement.TestUsers;
import com.classmanagement.common.AuditService;
import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import com.classmanagement.user.UserService;
import com.classmanagement.user.dto.CreateUserRequest;
import com.classmanagement.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import static org.mockito.ArgumentMatchers.contains;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutationAuditCoverageTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private AuditService audit;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        audit = mock(AuditService.class);
        service = new UserService(users, encoder, audit);
    }

    @Test
    void userCreationAuditContainsSafeBusinessFieldsOnly() {
        when(encoder.encode("Secret123")).thenReturn("bcrypt-secret-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 22L);
            return user;
        });

        service.create(new CreateUserRequest(
                "Nguyễn Văn A", "A@Test.Local", "Secret123", "teacher", "0901234567"), 1L);

        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(audit).audit(eq("users"), eq("users"), eq(22L), eq("create"),
                any(String.class), isNull(), values.capture(), eq(1L));
        String serialized = values.getValue().toString().toLowerCase();
        assertFalse(serialized.contains("password"));
        assertFalse(serialized.contains("secret"));
        assertFalse(serialized.contains("hash"));
    }

    @Test
    void adminPasswordResetWritesOnlySafePasswordChangedMetadata() {
        User target = TestUsers.teacher(22L);
        when(users.findById(22L)).thenReturn(Optional.of(target));
        when(encoder.encode("NewSecret123")).thenReturn("bcrypt-secret-hash");

        service.update(22L, new UpdateUserRequest(Map.of("password", "NewSecret123")), 1L);

        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(audit).audit(eq("authentication"), eq("users"), eq(22L), eq("update"),
                contains("đặt lại mật khẩu"), isNull(), values.capture(), eq(1L));
        String metadata = values.getValue().toString().toLowerCase();
        assertTrue(metadata.contains("password_changed=true"));
        assertFalse(metadata.contains("newsecret123"));
        assertFalse(metadata.contains("bcrypt-secret-hash"));
    }
    @Test
    void userUpdateDeactivateAndActivateAreAuditedByActor() {
        User target = TestUsers.teacher(22L);
        when(users.findById(22L)).thenReturn(Optional.of(target));

        service.update(22L, new UpdateUserRequest(Map.of("phone", "0911111111")), 1L);
        service.deactivate(22L, 1L);
        service.activate(22L, 1L);

        verify(audit).audit(eq("users"), eq("users"), eq(22L), eq("update"),
                contains("Cập nhật tài khoản"), any(), any(), eq(1L));
        verify(audit).audit(eq("users"), eq("users"), eq(22L), eq("update"),
                eq("Vô hiệu hóa tài khoản"), any(), any(), eq(1L));
        verify(audit).audit(eq("users"), eq("users"), eq(22L), eq("update"),
                eq("Kích hoạt tài khoản"), any(), any(), eq(1L));
    }
}
