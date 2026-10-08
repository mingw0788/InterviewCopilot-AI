package com.interviewcopilot.business.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class IdempotencyRequestHasher {
    private final ObjectMapper canonicalMapper;

    public IdempotencyRequestHasher(ObjectMapper objectMapper) {
        canonicalMapper = objectMapper.copy();
        canonicalMapper.setConfig(canonicalMapper.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    }

    public String hash(Object requestIdentity) {
        Objects.requireNonNull(requestIdentity, "requestIdentity is required");
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsString(requestIdentity)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("requestIdentity must be JSON serializable", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
