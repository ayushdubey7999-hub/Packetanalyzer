package com.packet.storage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Metadata for one capture session (used with auto-save logging).
 */
public final class CaptureSession {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final LocalDateTime startedAt;
    private final String interfaceName;
    private final String csvPath;
    private final String logPath;

    public CaptureSession(LocalDateTime startedAt, String interfaceName, String csvPath, String logPath) {
        this.startedAt = startedAt;
        this.interfaceName = interfaceName;
        this.csvPath = csvPath;
        this.logPath = logPath;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getCsvPath() {
        return csvPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public static String timestampForFilename() {
        return LocalDateTime.now().format(FILE_STAMP);
    }
}
