package com.classmanagement.security;

import com.classmanagement.config.AppJwtProperties;
import com.classmanagement.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// Token HMAC-SHA, payload {sub: user id, role} — cung dinh dang voi ban Node (jsonwebtoken)
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Secret dev mac dinh trong application.yml — chi de chay local, khong duoc dung khi deploy that. */
    private static final String DEV_DEFAULT_SECRET =
            "eab69b4c0f8cd35a6ea491242853b4d172b9e48ccb73b33f5e2db843d5c59e0a";

    private final SecretKey key;
    private final Duration expires;

    public JwtService(AppJwtProperties props) {
        String secret = props.secret();
        // jjwt tu choi key < 256 bit (WeakKeyException kho hieu luc boot) — bao loi ro rang truoc.
        // Luu y khi migrate: secret cu cua ban Node ngan hon 32 byte se KHONG dung duoc.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET phải dài tối thiểu 32 byte (yêu cầu của HS256/RFC 7518). "
                    + "Đặt biến môi trường JWT_SECRET bằng một chuỗi ngẫu nhiên đủ dài.");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("Đang dùng JWT secret dev mặc định trong application.yml — "
                    + "PHẢI đặt biến môi trường JWT_SECRET khi chạy thật (ai đọc được source đều tự ký được token admin).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expires = props.expires();
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expires)))
                .signWith(key)
                .compact();
    }

    /** Tra ve user id trong token; nem JwtException neu token sai/het han. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
