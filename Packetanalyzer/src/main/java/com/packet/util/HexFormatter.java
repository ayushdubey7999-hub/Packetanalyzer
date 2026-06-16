package com.packet.util;

/**
 * Formats raw bytes as a classic hex dump (offset | hex | ASCII).
 */
public final class HexFormatter {

    private static final int BYTES_PER_LINE = 16;

    private HexFormatter() {
    }

    public static String format(byte[] data) {
        if (data == null || data.length == 0) {
            return "(no payload)";
        }
        StringBuilder sb = new StringBuilder();
        for (int offset = 0; offset < data.length; offset += BYTES_PER_LINE) {
            sb.append(String.format("%08X  ", offset));
            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < BYTES_PER_LINE; i++) {
                int index = offset + i;
                if (index < data.length) {
                    int value = data[index] & 0xFF;
                    hex.append(String.format("%02X ", value));
                    char c = (char) value;
                    ascii.append(c >= 32 && c < 127 ? c : '.');
                } else {
                    hex.append("   ");
                }
            }
            sb.append(hex).append(" |").append(ascii).append("|\n");
        }
        return sb.toString();
    }
}
