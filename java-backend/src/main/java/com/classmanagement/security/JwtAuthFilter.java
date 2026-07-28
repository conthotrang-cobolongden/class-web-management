package com.classmanagement.security;

import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Nap user tu DB moi request (nhu ban Node) — tai khoan vua bi vo hieu hoa bi chan ngay (FR-1.3)
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Attribute chua thong bao loi cu the de AuthenticationEntryPoint tra ve dung message. */
    public static final String AUTH_ERROR = "jwt_auth_error";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Long userId = jwtService.parseUserId(token);
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isActive()) {
                    request.setAttribute(AUTH_ERROR, "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa");
                } else {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority(user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | NumberFormatException e) {
                request.setAttribute(AUTH_ERROR, "Phiên đăng nhập hết hạn hoặc không hợp lệ");
            }
        }
        filterChain.doFilter(request, response);
    }
}
