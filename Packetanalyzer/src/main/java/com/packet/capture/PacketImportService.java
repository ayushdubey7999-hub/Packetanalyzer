package com.packet.capture;

import com.packet.model.PacketInfo;
import com.packet.parser.PacketParser;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.BsdLoopbackPacket;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.LinuxSllPacket;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.UnknownPacket;

/**
 * Imports capture files into the existing packet model.
 */
public final class PacketImportService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int BATCH_SIZE = 250;
    private static final int LINKTYPE_NULL = 0;
    private static final int LINKTYPE_ETHERNET = 1;
    private static final int LINKTYPE_RAW = 101;
    private static final int LINKTYPE_LOOP = 108;
    private static final int LINKTYPE_LINUX_SLL = 113;
    private static final int LINKTYPE_LINUX_SLL2 = 276;
    private static final int PCAPNG_SECTION_HEADER = 0x0A0D0D0A;
    private static final int PCAPNG_INTERFACE_DESCRIPTION = 0x00000001;
    private static final int PCAPNG_ENHANCED_PACKET = 0x00000006;
    private static final int PCAPNG_SIMPLE_PACKET = 0x00000003;

    private final PacketParser packetParser = new PacketParser();
    private volatile boolean running;
    private Thread importThread;

    public void importCapture(
            Path file,
            Consumer<ImportProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer,
            Consumer<ImportSummary> completeConsumer,
            Consumer<Throwable> errorConsumer) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(progressConsumer, "progressConsumer");
        Objects.requireNonNull(batchConsumer, "batchConsumer");
        Objects.requireNonNull(completeConsumer, "completeConsumer");
        Objects.requireNonNull(errorConsumer, "errorConsumer");

        stop();
        running = true;
        importThread =
                new Thread(
                        () -> {
                            try {
                                ImportSummary summary = importFile(file, progressConsumer, batchConsumer);
                                completeConsumer.accept(summary);
                            } catch (Throwable t) {
                                errorConsumer.accept(t);
                            } finally {
                                running = false;
                            }
                        },
                        "packet-import");
        importThread.setDaemon(true);
        importThread.start();
    }

    public void stop() {
        running = false;
        Thread thread = importThread;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        importThread = null;
    }

    private ImportSummary importFile(
            Path file,
            Consumer<ImportProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws IOException {
        long totalBytes = Files.size(file);
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] magic = readExact(in, 4);
            if (matches(magic, 0x0A, 0x0D, 0x0D, 0x0A)) {
                return importPcapng(file, magic, in, totalBytes, progressConsumer, batchConsumer);
            }
            return importPcap(file, magic, in, totalBytes, progressConsumer, batchConsumer);
        }
    }

    private ImportSummary importPcap(
            Path file,
            byte[] magic,
            BufferedInputStream in,
            long totalBytes,
            Consumer<ImportProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws IOException {
        boolean littleEndian;
        boolean nanosecondPrecision;
        if (matches(magic, 0xD4, 0xC3, 0xB2, 0xA1)) {
            littleEndian = true;
            nanosecondPrecision = false;
        } else if (matches(magic, 0xA1, 0xB2, 0xC3, 0xD4)) {
            littleEndian = false;
            nanosecondPrecision = false;
        } else if (matches(magic, 0x4D, 0x3C, 0xB2, 0xA1)) {
            littleEndian = true;
            nanosecondPrecision = true;
        } else if (matches(magic, 0xA1, 0xB2, 0x3C, 0x4D)) {
            littleEndian = false;
            nanosecondPrecision = true;
        } else {
            throw new IOException("Unsupported pcap file: " + file.getFileName());
        }

        byte[] headerTail = readExact(in, 20);
        int linkType = readInt(headerTail, 16, littleEndian);
        int packetNumber = 0;
        List<PacketInfo> batch = new ArrayList<>(BATCH_SIZE);

        while (running) {
            byte[] recordHeader;
            try {
                recordHeader = readExactOrNull(in, 16);
            } catch (EOFException eof) {
                break;
            }
            if (recordHeader == null) {
                break;
            }

            long seconds = Integer.toUnsignedLong(readInt(recordHeader, 0, littleEndian));
            long fraction = Integer.toUnsignedLong(readInt(recordHeader, 4, littleEndian));
            long includedLength = Integer.toUnsignedLong(readInt(recordHeader, 8, littleEndian));
            int originalLength = readInt(recordHeader, 12, littleEndian);
            byte[] payload = readExact(in, safeLength(includedLength));
            packetNumber++;
            long nanos = nanosecondPrecision ? fraction : fraction * 1_000L;
            batch.add(parsePacket(packetNumber, payload, seconds, nanos, linkType, originalLength));
            emitBatchIfNeeded(batch, batchConsumer, packetNumber, in.available(), totalBytes, progressConsumer);
        }

        flushBatch(batch, batchConsumer);
        progressConsumer.accept(new ImportProgress(totalBytes, totalBytes, packetNumber, "Import complete"));
        return new ImportSummary(file, packetNumber, "Imported Capture");
    }

    private ImportSummary importPcapng(
            Path file,
            byte[] firstBlock,
            BufferedInputStream in,
            long totalBytes,
            Consumer<ImportProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws IOException {
        PcapngState state = new PcapngState();
        int packetNumber = 0;
        List<PacketInfo> batch = new ArrayList<>(BATCH_SIZE);
        byte[] blockTypeBytes = firstBlock;

        while (running) {
            byte[] header = blockTypeBytes;
            if (header == null) {
                header = readExactOrNull(in, 8);
                if (header == null) {
                    break;
                }
            }

            int blockType = readInt(header, 0, false);
            int blockLength = readInt(header, 4, state.littleEndian);
            if (blockLength < 12) {
                throw new IOException("Invalid pcapng block length: " + blockLength);
            }

            byte[] blockData = readExact(in, blockLength - 8);
            byte[] body = slice(blockData, 0, blockData.length - 4);
            int trailerLength = readInt(blockData, blockData.length - 4, state.littleEndian);
            if (trailerLength != blockLength) {
                throw new IOException("Corrupt pcapng block length.");
            }

            if (blockType == PCAPNG_SECTION_HEADER) {
                state.littleEndian = isLittleEndianPcapng(body);
            } else if (blockType == PCAPNG_INTERFACE_DESCRIPTION) {
                state.addInterface(parseInterfaceDescription(body, state.littleEndian));
            } else if (blockType == PCAPNG_ENHANCED_PACKET) {
                PcapngPacket packet = parseEnhancedPacket(body, state);
                if (packet != null) {
                    packetNumber++;
                    batch.add(parsePacket(packetNumber, packet.data(), packet.timestamp().getEpochSecond(), packet.timestamp().getNano(), packet.linkType(), packet.originalLength()));
                }
            } else if (blockType == PCAPNG_SIMPLE_PACKET) {
                PcapngPacket packet = parseSimplePacket(body, state);
                if (packet != null) {
                    packetNumber++;
                    batch.add(parsePacket(packetNumber, packet.data(), packet.timestamp().getEpochSecond(), packet.timestamp().getNano(), packet.linkType(), packet.originalLength()));
                }
            }

            emitBatchIfNeeded(batch, batchConsumer, packetNumber, in.available(), totalBytes, progressConsumer);
            blockTypeBytes = null;
        }

        flushBatch(batch, batchConsumer);
        progressConsumer.accept(new ImportProgress(totalBytes, totalBytes, packetNumber, "Import complete"));
        return new ImportSummary(file, packetNumber, "Imported Capture");
    }

    private void emitBatchIfNeeded(
            List<PacketInfo> batch,
            Consumer<List<PacketInfo>> batchConsumer,
            int packetNumber,
            int bytesRemainingEstimate,
            long totalBytes,
            Consumer<ImportProgress> progressConsumer) {
        if (batch.size() >= BATCH_SIZE) {
            flushBatch(batch, batchConsumer);
        }
        long bytesRead = Math.max(0L, totalBytes - bytesRemainingEstimate);
        progressConsumer.accept(new ImportProgress(bytesRead, totalBytes, packetNumber, "Imported " + packetNumber + " packets…"));
    }

    private void flushBatch(List<PacketInfo> batch, Consumer<List<PacketInfo>> batchConsumer) {
        if (!batch.isEmpty()) {
            batchConsumer.accept(List.copyOf(batch));
            batch.clear();
        }
    }

    private PacketInfo parsePacket(
            long packetNumber,
            byte[] payload,
            long seconds,
            long nanos,
            int linkType,
            int originalLength) {
        Packet packet = decodePacket(linkType, payload);
        String timestamp = formatTimestamp(seconds, nanos);
        PacketInfo info = packetParser.parse(packet, packetNumber, timestamp);
        if (info != null && originalLength > 0 && info.getLength() == 0) {
            info.setLength(originalLength);
        }
        return info;
    }

    private static String formatTimestamp(long seconds, long nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        return LocalTime.ofInstant(instant, ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    private static Packet decodePacket(int linkType, byte[] payload) {
        try {
            return switch (linkType) {
                case LINKTYPE_ETHERNET -> EthernetPacket.newPacket(payload, 0, payload.length);
                case LINKTYPE_LINUX_SLL, LINKTYPE_LINUX_SLL2 -> LinuxSllPacket.newPacket(payload, 0, payload.length);
                case LINKTYPE_NULL, LINKTYPE_LOOP -> BsdLoopbackPacket.newPacket(payload, 0, payload.length);
                case LINKTYPE_RAW -> decodeRawIpPacket(payload);
                default -> UnknownPacket.newPacket(payload, 0, payload.length);
            };
        } catch (Exception e) {
            return UnknownPacket.newPacket(payload, 0, payload.length);
        }
    }

    private static Packet decodeRawIpPacket(byte[] payload) {
        if (payload.length == 0) {
            return UnknownPacket.newPacket(payload, 0, payload.length);
        }
        int version = (payload[0] >> 4) & 0x0F;
        try {
            if (version == 4) {
                return IpV4Packet.newPacket(payload, 0, payload.length);
            }
            if (version == 6) {
                return IpV6Packet.newPacket(payload, 0, payload.length);
            }
        } catch (IllegalRawDataException e) {
            return UnknownPacket.newPacket(payload, 0, payload.length);
        }
        return UnknownPacket.newPacket(payload, 0, payload.length);
    }

    private static PcapngInterface parseInterfaceDescription(byte[] body, boolean littleEndian) throws IOException {
        if (body.length < 8) {
            throw new IOException("Malformed pcapng interface description block.");
        }
        int linkType = readUnsignedShort(body, 0, littleEndian);
        int snapLen = readInt(body, 4, littleEndian);
        return new PcapngInterface(linkType, snapLen, TimestampResolution.microseconds());
    }

    private static PcapngPacket parseEnhancedPacket(byte[] body, PcapngState state) throws IOException {
        if (body.length < 20) {
            throw new IOException("Malformed pcapng enhanced packet block.");
        }
        int interfaceId = readInt(body, 0, state.littleEndian);
        long tsHigh = Integer.toUnsignedLong(readInt(body, 4, state.littleEndian));
        long tsLow = Integer.toUnsignedLong(readInt(body, 8, state.littleEndian));
        int capturedLength = readInt(body, 12, state.littleEndian);
        int originalLength = readInt(body, 16, state.littleEndian);
        if (capturedLength < 0 || 20 + capturedLength > body.length) {
            throw new IOException("Malformed pcapng packet length.");
        }
        byte[] data = slice(body, 20, capturedLength);
        PcapngInterface iface = state.interfaceAt(interfaceId);
        TimestampResolution resolution = iface != null ? iface.resolution() : TimestampResolution.microseconds();
        Instant timestamp = resolution.toInstant((tsHigh << 32) | tsLow);
        int linkType = iface != null ? iface.linkType() : LINKTYPE_ETHERNET;
        return new PcapngPacket(data, timestamp, linkType, originalLength);
    }

    private static PcapngPacket parseSimplePacket(byte[] body, PcapngState state) throws IOException {
        if (body.length < 4) {
            throw new IOException("Malformed pcapng simple packet block.");
        }
        int originalLength = readInt(body, 0, state.littleEndian);
        int length = Math.min(originalLength, body.length - 4);
        byte[] data = slice(body, 4, length);
        PcapngInterface iface = state.interfaceAt(0);
        int linkType = iface != null ? iface.linkType() : LINKTYPE_ETHERNET;
        return new PcapngPacket(data, Instant.now(), linkType, originalLength);
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of file.");
            }
            offset += read;
        }
        return buffer;
    }

    private static byte[] readExactOrNull(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                return offset == 0 ? null : throwEof();
            }
            offset += read;
        }
        return buffer;
    }

    private static byte[] throwEof() throws EOFException {
        throw new EOFException("Unexpected end of file.");
    }

    private static boolean isLittleEndianPcapng(byte[] sectionHeaderBody) throws IOException {
        if (sectionHeaderBody.length < 8) {
            throw new IOException("Malformed pcapng section header.");
        }
        int bom = readInt(sectionHeaderBody, 0, false);
        if (bom == 0x1A2B3C4D) {
            return false;
        }
        if (bom == 0x4D3C2B1A) {
            return true;
        }
        throw new IOException("Unsupported pcapng byte-order marker.");
    }

    private static boolean matches(byte[] bytes, int b0, int b1, int b2, int b3) {
        return (bytes[0] & 0xFF) == b0
                && (bytes[1] & 0xFF) == b1
                && (bytes[2] & 0xFF) == b2
                && (bytes[3] & 0xFF) == b3;
    }

    private static int safeLength(long value) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IOException("Packet too large to import.");
        }
        return (int) value;
    }

    private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return (bytes[offset] & 0xFF)
                    | ((bytes[offset + 1] & 0xFF) << 8)
                    | ((bytes[offset + 2] & 0xFF) << 16)
                    | ((bytes[offset + 3] & 0xFF) << 24);
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static int readUnsignedShort(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
        }
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(source, offset, copy, 0, length);
        return copy;
    }

    private record PcapngInterface(int linkType, int snapLength, TimestampResolution resolution) {
    }

    private record PcapngPacket(byte[] data, Instant timestamp, int linkType, int originalLength) {
    }

    public record ImportSummary(Path file, int packetCount, String mode) {
    }

    public record ImportProgress(long bytesRead, long totalBytes, int packetCount, String message) {
    }

    private record TimestampResolution(boolean micros) {
        private static TimestampResolution microseconds() {
            return new TimestampResolution(true);
        }

        private Instant toInstant(long rawTimestamp) {
            if (micros) {
                long seconds = rawTimestamp / 1_000_000L;
                long micro = rawTimestamp % 1_000_000L;
                return Instant.ofEpochSecond(seconds, micro * 1_000L);
            }
            return Instant.ofEpochSecond(rawTimestamp);
        }
    }

    private static final class PcapngState {
        private boolean littleEndian;
        private final List<PcapngInterface> interfaces = new ArrayList<>();

        private void addInterface(PcapngInterface iface) {
            interfaces.add(iface);
        }

        private PcapngInterface interfaceAt(int index) {
            if (index < 0 || index >= interfaces.size()) {
                return null;
            }
            return interfaces.get(index);
        }
    }
}
