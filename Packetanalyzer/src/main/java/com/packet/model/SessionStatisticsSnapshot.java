package com.packet.model;

/** Persisted capture counters stored inside a session file. */
public final class SessionStatisticsSnapshot {

    private long total;
    private long tcp;
    private long udp;
    private long ipv4;
    private long ipv6;
    private long icmp;

    public SessionStatisticsSnapshot() {
    }

    public SessionStatisticsSnapshot(long total, long tcp, long udp, long ipv4, long ipv6, long icmp) {
        this.total = total;
        this.tcp = tcp;
        this.udp = udp;
        this.ipv4 = ipv4;
        this.ipv6 = ipv6;
        this.icmp = icmp;
    }

    public static SessionStatisticsSnapshot from(CaptureStatistics statistics) {
        return new SessionStatisticsSnapshot(
                statistics.getTotal(),
                statistics.getTcp(),
                statistics.getUdp(),
                statistics.getIpv4(),
                statistics.getIpv6(),
                statistics.getIcmp());
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getTcp() {
        return tcp;
    }

    public void setTcp(long tcp) {
        this.tcp = tcp;
    }

    public long getUdp() {
        return udp;
    }

    public void setUdp(long udp) {
        this.udp = udp;
    }

    public long getIpv4() {
        return ipv4;
    }

    public void setIpv4(long ipv4) {
        this.ipv4 = ipv4;
    }

    public long getIpv6() {
        return ipv6;
    }

    public void setIpv6(long ipv6) {
        this.ipv6 = ipv6;
    }

    public long getIcmp() {
        return icmp;
    }

    public void setIcmp(long icmp) {
        this.icmp = icmp;
    }
}
