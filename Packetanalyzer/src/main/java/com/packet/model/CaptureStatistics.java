package com.packet.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe live counters updated on the capture thread, read on the JavaFX thread.
 *
 * <p>Using {@link AtomicLong} avoids locking when packets arrive quickly.
 */
public final class CaptureStatistics {

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong tcp = new AtomicLong();
    private final AtomicLong udp = new AtomicLong();
    private final AtomicLong ipv4 = new AtomicLong();
    private final AtomicLong ipv6 = new AtomicLong();
    private final AtomicLong icmp = new AtomicLong();

    public void record(PacketInfo info) {
        if (info == null) {
            return;
        }
        total.incrementAndGet();
        String protocol = info.getProtocol();
        if ("TCP".equalsIgnoreCase(protocol)) {
            tcp.incrementAndGet();
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            udp.incrementAndGet();
        } else if ("ICMP".equalsIgnoreCase(protocol)) {
            icmp.incrementAndGet();
        }
        String version = info.getIpVersion();
        if ("IPv4".equalsIgnoreCase(version)) {
            ipv4.incrementAndGet();
        } else if ("IPv6".equalsIgnoreCase(version)) {
            ipv6.incrementAndGet();
        }
    }

    public void reset() {
        total.set(0);
        tcp.set(0);
        udp.set(0);
        ipv4.set(0);
        ipv6.set(0);
        icmp.set(0);
    }

    public long getTotal() {
        return total.get();
    }

    public long getTcp() {
        return tcp.get();
    }

    public long getUdp() {
        return udp.get();
    }

    public long getIpv4() {
        return ipv4.get();
    }

    public long getIpv6() {
        return ipv6.get();
    }

    public long getIcmp() {
        return icmp.get();
    }
}
