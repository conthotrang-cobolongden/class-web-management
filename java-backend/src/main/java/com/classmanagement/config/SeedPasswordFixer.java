package com.classmanagement.config;

import com.classmanagement.user.User;
import com.classmanagement.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Seed (V2) chen user voi hash placeholder "$2b$10$REPLACE_ME" — thay bang bcrypt that
// cua mat khau mac dinh Admin@123 ngay khi app khoi dong (thay cho script node cu).
@Component
@ConditionalOnProperty(name = "app.seed.fix-placeholder-passwords", havingValue = "true", matchIfMissing = true)
public class SeedPasswordFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedPasswordFixer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public SeedPasswordFixer(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> pending = users.findByPasswordHashContaining("REPLACE_ME");
        if (pending.isEmpty()) {
            return;
        }
        String hash = encoder.encode("Admin@123");
        pending.forEach(u -> u.setPasswordHash(hash));
        users.saveAll(pending);
        log.info("Đã đặt mật khẩu mặc định Admin@123 cho {} tài khoản seed", pending.size());
    }
}
