package com.jackvanlightly.rabbittesttool.clients;

import java.nio.ByteBuffer;

public class ByteUtils {
    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
