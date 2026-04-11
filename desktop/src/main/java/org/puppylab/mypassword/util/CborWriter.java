package org.puppylab.mypassword.util;

import java.io.ByteArrayOutputStream;

/**
 * Minimal CBOR (RFC 8949) encoder sufficient for building WebAuthn
 * attestationObject and COSE_Key structures.
 *
 * <p>
 * Supported major types:
 * <ul>
 *   <li>0 — unsigned integer</li>
 *   <li>1 — negative integer</li>
 *   <li>2 — byte string</li>
 *   <li>3 — text string (UTF-8)</li>
 *   <li>5 — map (definite length)</li>
 * </ul>
 */
public class CborWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    /** Write the initial byte for a given major type and argument value. */
    private void writeTypeAndLength(int majorType, long value) {
        int mt = (majorType & 0x07) << 5;
        if (value < 0) {
            throw new IllegalArgumentException("CBOR length/value must be non-negative: " + value);
        }
        if (value < 24) {
            out.write(mt | (int) value);
        } else if (value < 0x100L) {
            out.write(mt | 24);
            out.write((int) value);
        } else if (value < 0x10000L) {
            out.write(mt | 25);
            out.write((int) (value >> 8) & 0xff);
            out.write((int) value & 0xff);
        } else if (value < 0x100000000L) {
            out.write(mt | 26);
            out.write((int) (value >> 24) & 0xff);
            out.write((int) (value >> 16) & 0xff);
            out.write((int) (value >> 8) & 0xff);
            out.write((int) value & 0xff);
        } else {
            out.write(mt | 27);
            out.write((int) (value >> 56) & 0xff);
            out.write((int) (value >> 48) & 0xff);
            out.write((int) (value >> 40) & 0xff);
            out.write((int) (value >> 32) & 0xff);
            out.write((int) (value >> 24) & 0xff);
            out.write((int) (value >> 16) & 0xff);
            out.write((int) (value >> 8) & 0xff);
            out.write((int) value & 0xff);
        }
    }

    /** Encode a signed integer (major type 0 for non-negative, 1 for negative). */
    public CborWriter writeInt(long value) {
        if (value >= 0) {
            writeTypeAndLength(0, value);
        } else {
            writeTypeAndLength(1, -1 - value);
        }
        return this;
    }

    /** Encode a byte string (major type 2). */
    public CborWriter writeBytes(byte[] value) {
        writeTypeAndLength(2, value.length);
        out.write(value, 0, value.length);
        return this;
    }

    /** Encode a UTF-8 text string (major type 3). */
    public CborWriter writeText(String value) {
        byte[] utf8 = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeTypeAndLength(3, utf8.length);
        out.write(utf8, 0, utf8.length);
        return this;
    }

    /** Start a definite-length map (major type 5). Caller writes {@code size} key/value pairs. */
    public CborWriter writeMapHeader(int size) {
        writeTypeAndLength(5, size);
        return this;
    }
}
