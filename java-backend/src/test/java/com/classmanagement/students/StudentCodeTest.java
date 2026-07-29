package com.classmanagement.students;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentCodeTest {

    @Test
    void formatsInternalIdAsStableStudentCode() {
        assertEquals("HS000001", StudentCodes.fromId(1L));
        assertEquals("HS123456", StudentCodes.fromId(123456L));
        assertEquals("HS1234567", StudentCodes.fromId(1234567L));
    }

    @Test
    void rejectsMissingOrInvalidInternalId() {
        assertThrows(IllegalArgumentException.class, () -> StudentCodes.fromId(null));
        assertThrows(IllegalArgumentException.class, () -> StudentCodes.fromId(0L));
    }
}
