package com.packet.storage;

import com.packet.model.PacketInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Saves captured packets to CSV and TXT files under {@code captures/}.
 *
 * <p><b>FileWriter / BufferedWriter / try-with-resources:</b>
 * We open files inside try-with-resources so Java closes writers automatically, even on errors.
 *
 * <p><b>Thread safety:</b> {@link #appendPacket} is {@code synchronized} so the capture thread can
 * append while another action saves — one writer at a time per session.
 */
public final class CaptureFileManager {

    public static final String CAPTURES_DIR = "captures";

    private static final String CSV_HEADER =
            "PacketNo,Timestamp,SourceIP,DestinationIP,SourcePort,DestinationPort,Protocol,Length,TCPFlags,Info";

    private BufferedWriter csvWriter;
    private BufferedWriter logWriter;
    private CaptureSession activeSession;
    private final Object writeLock = new Object();

    public static Path ensureCapturesDirectory() throws IOException {
        Path dir = Paths.get(CAPTURES_DIR);
        Files.createDirectories(dir);
        return dir;
    }

    public static Path newCaptureCsvPath() throws IOException {
        ensureCapturesDirectory();
        return Paths.get(CAPTURES_DIR, "capture_" + CaptureSession.timestampForFilename() + ".csv");
    }

    public static Path newCaptureLogPath() throws IOException {
        ensureCapturesDirectory();
        return Paths.get(CAPTURES_DIR, "capture_" + CaptureSession.timestampForFilename() + ".log");
    }

    /**
     * Opens CSV + log files for continuous auto-save during capture.
     */
    public CaptureSession startAutoSaveSession(String interfaceName) throws IOException {
        closeAutoSaveSession();

        Path csvPath = newCaptureCsvPath();
        Path logPath = newCaptureLogPath();

        synchronized (writeLock) {
            csvWriter = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            logWriter = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            csvWriter.write(CSV_HEADER);
            csvWriter.newLine();
            logWriter.write("# Capture session started");
            logWriter.newLine();
            logWriter.write("# Interface: " + interfaceName);
            logWriter.newLine();
            logWriter.flush();
        }

        activeSession =
                new CaptureSession(
                        java.time.LocalDateTime.now(),
                        interfaceName,
                        csvPath.toString(),
                        logPath.toString());
        return activeSession;
    }

    /**
     * Appends one packet to the active auto-save session (capture thread safe).
     */
    public void appendPacket(PacketInfo packet) throws IOException {
        if (packet == null) {
            return;
        }
        synchronized (writeLock) {
            if (csvWriter == null || logWriter == null) {
                return;
            }
            csvWriter.write(toCsvLine(packet));
            csvWriter.newLine();
            logWriter.write(toLogLine(packet));
            logWriter.newLine();
            csvWriter.flush();
            logWriter.flush();
        }
    }

    public void closeAutoSaveSession() {
        synchronized (writeLock) {
            closeQuietly(csvWriter);
            closeQuietly(logWriter);
            csvWriter = null;
            logWriter = null;
            activeSession = null;
        }
    }

    public CaptureSession getActiveSession() {
        return activeSession;
    }

    /**
     * Saves all packets from memory to a new timestamped CSV in {@code captures/}.
     */
    public Path saveAllToCapturesFolder(List<PacketInfo> packets) throws IOException {
        Path path = newCaptureCsvPath();
        writeCsv(path, packets);
        return path;
    }

    /**
     * Exports packets to a user-chosen path (FileChooser destination).
     */
    public void exportToCsv(Path destination, List<PacketInfo> packets) throws IOException {
        writeCsv(destination, packets);
    }

    private static void writeCsv(Path path, List<PacketInfo> packets) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (PacketInfo packet : packets) {
                writer.write(toCsvLine(packet));
                writer.newLine();
            }
        }
    }

    private static String toCsvLine(PacketInfo p) {
        return String.join(
                ",",
                escape(String.valueOf(p.getPacketNumber())),
                escape(p.getTimestamp()),
                escape(p.getSourceIp()),
                escape(p.getDestinationIp()),
                escape(p.getSourcePort()),
                escape(p.getDestinationPort()),
                escape(p.getProtocol()),
                escape(String.valueOf(p.getLength())),
                escape(p.getTcpFlags()),
                escape(p.getInfo()));
    }

    private static String toLogLine(PacketInfo p) {
        return String.format(
                "[%s] #%d %s %s:%s -> %s:%s len=%d flags=%s | %s",
                p.getTimestamp(),
                p.getPacketNumber(),
                p.getProtocol(),
                p.getSourceIp(),
                p.getSourcePort(),
                p.getDestinationIp(),
                p.getDestinationPort(),
                p.getLength(),
                p.getTcpFlags(),
                p.getInfo());
    }

    /** Wrap fields that may contain commas in double quotes. */
    private static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Best effort close.
            }
        }
    }
}
