package com.classmanagement.user;

import com.classmanagement.common.ApiException;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.user.dto.CreateUserRequest;
import com.classmanagement.user.dto.UpdateUserRequest;
import com.classmanagement.user.dto.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService {

    public static final List<String> ROLES = List.of("admin", "teacher", "assistant");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public static String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static void assertRole(String role) {
        if (role == null || !ROLES.contains(role)) {
            throw new ApiException(400, "Trường role phải là một trong: " + String.join(", ", ROLES));
        }
    }

    public PageResponse<UserDto> list(Pagination p, String role, String activeParam) {
        Pageable pageable = PageRequest.of(p.page() - 1, p.pageSize(), Sort.by("id"));
        boolean onlyActive = "true".equals(activeParam);
        boolean hasRole = role != null && !role.isBlank();

        Page<User> result;
        if (hasRole && onlyActive) {
            result = users.findByRoleAndIsActiveTrue(role, pageable);
        } else if (hasRole) {
            result = users.findByRole(role, pageable);
        } else if (onlyActive) {
            result = users.findByIsActiveTrue(pageable);
        } else {
            result = users.findAll(pageable);
        }
        return new PageResponse<>(result.getContent().stream().map(UserDto::from).toList(),
                result.getTotalElements(), p.page(), p.pageSize());
    }

    @Transactional
    public UserDto create(CreateUserRequest req, Long actorId) {
        assertRole(req.role());
        User u = new User();
        u.setFullName(req.fullName());
        u.setEmail(normalizeEmail(req.email()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        u.setPhone(emptyToNull(req.phone()));
        users.save(u);
        audit.audit("users", "users", u.getId(), "create",
                "Tạo tài khoản " + u.getEmail(), null, snapshot(u), actorId);
        return UserDto.from(u);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req, Long currentUserId) {
        User u = users.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));

        Map<String, Object> oldValues = snapshot(u);
        boolean changed = false;
        if (req.has("full_name")) {
            u.setFullName(req.get("full_name"));
            changed = true;
        }
        if (req.has("phone")) {
            u.setPhone(emptyToNull(req.get("phone")));
            changed = true;
        }
        if (req.has("email")) {
            String email = req.get("email");
            u.setEmail(email == null ? null : normalizeEmail(email));
            changed = true;
        }
        if (req.has("role")) {
            String role = req.get("role");
            assertRole(role);
            // Chan admin tu ha quyen chinh minh (cung ly do voi guard tu-deactivate):
            // he thong co the mat admin cuoi cung, chi cuu duoc bang cach sua DB tay
            if (id.equals(currentUserId) && !role.equals(u.getRole())) {
                throw new ApiException(400, "Không thể tự thay đổi vai trò của chính mình");
            }
            u.setRole(role);
            changed = true;
        }
        // Admin dat lai mat khau cho user quen (khong can mat khau cu); null/"" bo qua nhu Node
        String password = req.has("password") ? req.get("password") : null;
        boolean passwordChanged = false;
        if (password != null && !password.isEmpty()) {
            if (password.length() < 6) {
                throw new ApiException(400, "Mật khẩu phải có ít nhất 6 ký tự");
            }
            u.setPasswordHash(encoder.encode(password));
            passwordChanged = true;
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }
        users.save(u);
        audit.audit("users", "users", u.getId(), "update",
                "Cập nhật tài khoản " + u.getEmail(), oldValues, snapshot(u), currentUserId);
        if (passwordChanged) {
            audit.audit("authentication", "users", u.getId(), "update",
                    "Admin đặt lại mật khẩu", null,
                    Map.of("password_changed", true), currentUserId);
        }
        return UserDto.from(u);
    }

    // FR-1.3: vo hieu hoa / kich hoat lai — khong xoa cung
    @Transactional
    public void deactivate(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new ApiException(400, "Không thể tự vô hiệu hóa tài khoản của chính mình");
        }
        User u = users.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));
        Map<String, Object> oldValues = snapshot(u);
        u.setActive(false);
        users.save(u);
        audit.audit("users", "users", u.getId(), "update", "Vô hiệu hóa tài khoản",
                oldValues, snapshot(u), currentUserId);
    }

    @Transactional
    public void activate(Long id, Long currentUserId) {
        User u = users.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));
        Map<String, Object> oldValues = snapshot(u);
        u.setActive(true);
        users.save(u);
        audit.audit("users", "users", u.getId(), "update", "Kích hoạt tài khoản",
                oldValues, snapshot(u), currentUserId);
    }

    private static Map<String, Object> snapshot(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", user.getId());
        values.put("full_name", user.getFullName());
        values.put("email", user.getEmail());
        values.put("role", user.getRole());
        values.put("phone", user.getPhone());
        values.put("is_active", user.isActive());
        return values;
    }
}
