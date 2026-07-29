package com.classmanagement;

import com.classmanagement.user.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestUsers {

    private TestUsers() {
    }

    public static User admin(long id) {
        return user(id, "admin");
    }

    public static User teacher(long id) {
        return user(id, "teacher");
    }

    public static User assistant(long id) {
        return user(id, "assistant");
    }

    private static User user(long id, String role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("Test " + role);
        user.setEmail(role + id + "@test.local");
        user.setPasswordHash("not-used");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
