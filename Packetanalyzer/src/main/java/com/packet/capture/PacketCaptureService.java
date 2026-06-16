package com.packet.capture;

import com.packet.util.MathUtil;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.packet.Packet;

/**
 * Owns the live capture loop and {@link PcapHandle} lifecycle on a background thread.
 */
public final class PacketCaptureService {

    private static final int DEFAULT_SNAPLEN = 65_536;
    private static final int READ_TIMEOUT_MS = 10;

    private final AtomicLong packetSequence = new AtomicLong(0);

    private volatile boolean running;
    private Thread captureThread;
    private volatile PcapHandle handle;

    public void startCapture(
            PcapNetworkInterface networkInterface,
            int snapLength,
            BiConsumer<Packet, Long> packetHandler,
            Consumer<Throwable> errorHandler) {
        Objects.requireNonNull(networkInterface, "networkInterface");
        Objects.requireNonNull(packetHandler, "packetHandler");

        stopCapture();
        packetSequence.set(0);
        running = true;
        int effectiveSnapLen = normalizeSnapLength(snapLength);

        captureThread =
                new Thread(
                        () -> runCaptureLoop(networkInterface, effectiveSnapLen, packetHandler, errorHandler),
                        "packet-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private static int normalizeSnapLength(int snapLength) {
        return MathUtil.clamp(snapLength, 1_500, DEFAULT_SNAPLEN);
    }

    private void runCaptureLoop(
            PcapNetworkInterface networkInterface,
            int snapLength,
            BiConsumer<Packet, Long> packetHandler,
            Consumer<Throwable> errorHandler) {
        PcapHandle localHandle = null;
        try {
            localHandle =
                    networkInterface.openLive(
                            snapLength, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, READ_TIMEOUT_MS);
            handle = localHandle;

            localHandle.setFilter("tcp or udp or icmp", BpfCompileMode.OPTIMIZE);

            while (running) {
                Packet packet = localHandle.getNextPacket();
                if (packet == null) {
                    continue;
                }
                long number = packetSequence.incrementAndGet();
                packetHandler.accept(packet, number);
            }
        } catch (PcapNativeException e) {
            if (running && errorHandler != null) {
                errorHandler.accept(e);
            }
        } catch (NotOpenException e) {
            if (running && errorHandler != null) {
                errorHandler.accept(e);
            }
        } finally {
            running = false;
            handle = null;
            if (localHandle != null && localHandle.isOpen()) {
                localHandle.close();
            }
        }
    }

    public void stopCapture() {
        running = false;
        Thread thread = captureThread;
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        captureThread = null;
    }

    public boolean isRunning() {
        return running;
    }
}
