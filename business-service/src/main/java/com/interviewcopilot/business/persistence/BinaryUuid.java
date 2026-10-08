package com.interviewcopilot.business.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Converts the existing, unswapped BINARY(16) storage without database-specific UUID functions. */
public final class BinaryUuid {
    private BinaryUuid() { }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("Expected a 16-byte UUID");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
