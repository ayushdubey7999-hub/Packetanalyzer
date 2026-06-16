package com.packet.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * One row in the packet table.
 *
 * <p>JavaFX properties let {@link javafx.scene.control.TableView} bind columns by name.
 * Extra fields ({@code ipVersion}, {@code detailText}) support filtering, row colors, and the
 * details panel without keeping raw Pcap4J objects in memory.
 */
public final class PacketInfo {

    private final LongProperty packetNumber = new SimpleLongProperty(this, "packetNumber", 0);
    private final StringProperty timestamp = new SimpleStringProperty(this, "timestamp", "");
    private final StringProperty sourceIp = new SimpleStringProperty(this, "sourceIp", "");
    private final StringProperty destinationIp = new SimpleStringProperty(this, "destinationIp", "");
    private final StringProperty sourcePort = new SimpleStringProperty(this, "sourcePort", "-");
    private final StringProperty destinationPort = new SimpleStringProperty(this, "destinationPort", "-");
    private final StringProperty protocol = new SimpleStringProperty(this, "protocol", "");
    private final StringProperty ipVersion = new SimpleStringProperty(this, "ipVersion", "");
    private final IntegerProperty length = new SimpleIntegerProperty(this, "length", 0);
    private final StringProperty tcpFlags = new SimpleStringProperty(this, "tcpFlags", "-");
    private final StringProperty info = new SimpleStringProperty(this, "info", "");
    private final StringProperty detailText = new SimpleStringProperty(this, "detailText", "");

    public LongProperty packetNumberProperty() {
        return packetNumber;
    }

    public long getPacketNumber() {
        return packetNumber.get();
    }

    public void setPacketNumber(long value) {
        packetNumber.set(value);
    }

    public StringProperty timestampProperty() {
        return timestamp;
    }

    public String getTimestamp() {
        return timestamp.get();
    }

    public void setTimestamp(String value) {
        timestamp.set(value != null ? value : "");
    }

    public StringProperty sourceIpProperty() {
        return sourceIp;
    }

    public String getSourceIp() {
        return sourceIp.get();
    }

    public void setSourceIp(String value) {
        sourceIp.set(value != null ? value : "");
    }

    public StringProperty destinationIpProperty() {
        return destinationIp;
    }

    public String getDestinationIp() {
        return destinationIp.get();
    }

    public void setDestinationIp(String value) {
        destinationIp.set(value != null ? value : "");
    }

    public StringProperty sourcePortProperty() {
        return sourcePort;
    }

    public String getSourcePort() {
        return sourcePort.get();
    }

    public void setSourcePort(String value) {
        sourcePort.set(value != null && !value.isBlank() ? value : "-");
    }

    public StringProperty destinationPortProperty() {
        return destinationPort;
    }

    public String getDestinationPort() {
        return destinationPort.get();
    }

    public void setDestinationPort(String value) {
        destinationPort.set(value != null && !value.isBlank() ? value : "-");
    }

    public StringProperty protocolProperty() {
        return protocol;
    }

    public String getProtocol() {
        return protocol.get();
    }

    public void setProtocol(String value) {
        protocol.set(value != null ? value : "");
    }

    public StringProperty ipVersionProperty() {
        return ipVersion;
    }

    public String getIpVersion() {
        return ipVersion.get();
    }

    public void setIpVersion(String value) {
        ipVersion.set(value != null ? value : "");
    }

    public IntegerProperty lengthProperty() {
        return length;
    }

    public int getLength() {
        return length.get();
    }

    public void setLength(int value) {
        length.set(value);
    }

    public StringProperty tcpFlagsProperty() {
        return tcpFlags;
    }

    public String getTcpFlags() {
        return tcpFlags.get();
    }

    public void setTcpFlags(String value) {
        tcpFlags.set(value != null && !value.isBlank() ? value : "-");
    }

    public StringProperty infoProperty() {
        return info;
    }

    public String getInfo() {
        return info.get();
    }

    public void setInfo(String value) {
        info.set(value != null ? value : "");
    }

    public StringProperty detailTextProperty() {
        return detailText;
    }

    public String getDetailText() {
        return detailText.get();
    }

    public void setDetailText(String value) {
        detailText.set(value != null ? value : "");
    }

    /** CSS style class for protocol-based row coloring (TCP, UDP, ICMP). */
    public String rowStyleClass() {
        if ("ICMP".equalsIgnoreCase(getProtocol())) {
            return "table-row-icmp";
        }
        if ("TCP".equalsIgnoreCase(getProtocol())) {
            return "table-row-tcp";
        }
        if ("UDP".equalsIgnoreCase(getProtocol())) {
            return "table-row-udp";
        }
        return "";
    }
}
