package com.interviewcopilot.business.idempotency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyKeyTests {
    @Test
    void acceptsTheFrozenVisibleAsciiRangeAndLength() {
        String minimum = "12345678";
        String maximum = "!".repeat(128);

        assertEquals(minimum, new IdempotencyKey(minimum).value());
        assertEquals(maximum, new IdempotencyKey(maximum).value());
    }

    @Test
    void rejectsWhitespaceControlUnicodeAndOutOfRangeLengths() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("1234567"));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("!".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("key with space"));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("key\nline1"));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("幂等键-12345678"));
    }
}
