package com.packet.model;

/**
 * Plain-data snapshot of a {@link PacketInfo} row for JSON session serialization.
 *
 * <p>Kept separate from the JavaFX property model so serialization stays UI-free.
 */
public final class PacketSnapshot {

    private long packetNumber;
    private String timestamp = "";
    private String sourceIp = "";
    private String destinationIp = "";
    private String sourcePort = "-";
    private String destinationPort = "-";
    private String protocol = "";
    private String ipVersion = "";
    private int length;
    private String tcpFlags = "-";
    private String info = "";
    private String detailText = "";

    public PacketSnapshot() {
    }

    public static PacketSnapshot from(PacketInfo packet) {
        PacketSnapshot snapshot = new PacketSnapshot();
        snapshot.packetNumber = packet.getPacketNumber();
        snapshot.timestamp = packet.getTimestamp();
        snapshot.sourceIp = packet.getSourceIp();
        snapshot.destinationIp = packet.getDestinationIp();
        snapshot.sourcePort = packet.getSourcePort();
        snapshot.destinationPort = packet.getDestinationPort();
        snapshot.protocol = packet.getProtocol();
        snapshot.ipVersion = packet.getIpVersion();
        snapshot.length = packet.getLength();
        snapshot.tcpFlags = packet.getTcpFlags();
        snapshot.info = packet.getInfo();
        snapshot.detailText = packet.getDetailText();
        return snapshot;
    }

    public PacketInfo toPacketInfo() {
        PacketInfo info = new PacketInfo();
        info.setPacketNumber(packetNumber);
        info.setTimestamp(timestamp);
        info.setSourceIp(sourceIp);
        info.setDestinationIp(destinationIp);
        info.setSourcePort(sourcePort);
        info.setDestinationPort(destinationPort);
        info.setProtocol(protocol);
        info.setIpVersion(ipVersion);
        info.setLength(length);
        info.setTcpFlags(tcpFlags);
        info.setInfo(this.info);
        info.setDetailText(detailText);
        return info;
    }

    public long getPacketNumber() {
        return packetNumber;
    }

    public void setPacketNumber(long packetNumber) {
        this.packetNumber = packetNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp != null ? timestamp : "";
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp != null ? sourceIp : "";
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(String destinationIp) {
        this.destinationIp = destinationIp != null ? destinationIp : "";
    }

    public String getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(String sourcePort) {
        this.sourcePort = sourcePort != null && !sourcePort.isBlank() ? sourcePort : "-";
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort != null && !destinationPort.isBlank() ? destinationPort : "-";
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol != null ? protocol : "";
    }

    public String getIpVersion() {
        return ipVersion;
    }

    public void setIpVersion(String ipVersion) {
        this.ipVersion = ipVersion != null ? ipVersion : "";
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getTcpFlags() {
        return tcpFlags;
    }

    public void setTcpFlags(String tcpFlags) {
        this.tcpFlags = tcpFlags != null && !tcpFlags.isBlank() ? tcpFlags : "-";
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info != null ? info : "";
    }

    public String getDetailText() {
        return detailText;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText != null ? detailText : "";
    }
}
