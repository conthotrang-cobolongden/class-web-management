package com.classmanagement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHarnessSmokeTest {

    @Test
    void runsJUnit() {
        assertEquals("assistant", TestUsers.assistant(3L).getRole());
    }
}
