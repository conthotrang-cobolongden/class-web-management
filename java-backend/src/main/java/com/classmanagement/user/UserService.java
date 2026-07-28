package com.classmanagement.user;

import com.classmanagement.common.ApiException;
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

@Service
public class UserService {

    public static final List<String> ROLES = List.of("admin", "teacher", "assistant");

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
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
    public UserDto create(CreateUserRequest req) {
        assertRole(req.role());
        User u = new User();
        u.setFullName(req.fullName());
        u.setEmail(normalizeEmail(req.email()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        u.setPhone(emptyToNull(req.phone()));
        users.save(u);
        return UserDto.from(u);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req, Long currentUserId) {
        User u = users.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));

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
        if (password != null && !password.isEmpty()) {
            if (password.length() < 6) {
                throw new ApiException(400, "Mật khẩu phải có ít nhất 6 ký tự");
            }
            u.setPasswordHash(encoder.encode(password));
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Không có gì để cập nhật");
        }
        users.save(u);
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
        u.setActive(false);
        users.save(u);
    }

    @Transactional
    public void activate(Long id) {
        User u = users.findById(id)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản"));
        u.setActive(true);
        users.save(u);
    }
}
