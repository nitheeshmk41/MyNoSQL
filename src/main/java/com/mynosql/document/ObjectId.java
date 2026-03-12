package com.mynosql.document;

import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MongoDB-compatible ObjectId generator.
 * 12-byte ID: 4-byte timestamp + 5-byte random + 3-byte counter
 */
public class ObjectId {

    private static final AtomicInteger COUNTER = new AtomicInteger(new SecureRandom().nextInt());
    private static final byte[] MACHINE_IDENTIFIER = generateMachineId();

    private final byte[] bytes;

    public ObjectId() {
        this.bytes = generate();
    }

    public ObjectId(String hex) {
        if (hex == null || hex.length() != 24) {
            throw new IllegalArgumentException("Invalid ObjectId hex string: " + hex);
        }
        this.bytes = hexToBytes(hex);
    }

    private static byte[] generate() {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        int counter = COUNTER.getAndIncrement() & 0x00FFFFFF;

        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(timestamp);
        buffer.put(MACHINE_IDENTIFIER, 0, 5);
        buffer.put((byte) (counter >> 16));
        buffer.put((byte) (counter >> 8));
        buffer.put((byte) counter);
        return buffer.array();
    }

    private static byte[] generateMachineId() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    buffer.put(mac, 0, Math.min(mac.length, buffer.remaining()));
                    if (!buffer.hasRemaining()) break;
                }
            }
            byte[] result = new byte[5];
            System.arraycopy(buffer.array(), 0, result, 0, 5);
            return result;
        } catch (Exception e) {
            byte[] fallback = new byte[5];
            new SecureRandom().nextBytes(fallback);
            return fallback;
        }
    }

    public long getTimestamp() {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, 4);
        return ((long) buffer.getInt()) * 1000L;
    }

    @Override
    public String toString() {
        return bytesToHex(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectId other)) return false;
        return java.util.Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
