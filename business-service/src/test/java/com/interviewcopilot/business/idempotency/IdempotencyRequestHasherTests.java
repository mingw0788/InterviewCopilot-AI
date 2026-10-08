package com.interviewcopilot.business.idempotency;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IdempotencyRequestHasherTests {
    private final IdempotencyRequestHasher hasher =
            new IdempotencyRequestHasher(JsonMapper.builder().build());

    @Test
    void producesCanonicalSha256ForEquivalentObjects() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("position", "Java Developer");
        first.put("question_count", 5);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("question_count", 5);
        second.put("position", "Java Developer");

        assertEquals(hasher.hash(first), hasher.hash(second));
        assertEquals(64, hasher.hash(first).length());
        assertNotEquals(hasher.hash(first), hasher.hash(Map.of("position", "Python Developer")));
    }
}
