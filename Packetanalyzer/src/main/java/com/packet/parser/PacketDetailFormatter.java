package com.packet.parser;

import com.packet.util.HexFormatter;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IcmpV4CommonPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;

/**
 * Builds the multi-line text shown in the packet details panel.
 *
 * <p>Runs on the capture thread — no JavaFX here.
 */
public final class PacketDetailFormatter {

    private PacketDetailFormatter() {
    }

    public static String format(Packet packet, String tcpFlags) {
        if (packet == null) {
            return "(no packet)";
        }

        StringBuilder sb = new StringBuilder();
        appendEthernet(sb, packet);
        appendIp(sb, packet);
        appendTransport(sb, packet, tcpFlags);
        appendHex(sb, packet);
        return sb.toString();
    }

    private static void appendEthernet(StringBuilder sb, Packet packet) {
        sb.append("=== Ethernet ===\n");
        if (!packet.contains(EthernetPacket.class)) {
            sb.append("  (not present or not parsed)\n\n");
            return;
        }
        try {
            EthernetPacket eth = packet.get(EthernetPacket.class);
            EthernetPacket.EthernetHeader header = eth.getHeader();
            sb.append("  Destination MAC : ").append(header.getDstAddr()).append('\n');
            sb.append("  Source MAC      : ").append(header.getSrcAddr()).append('\n');
            sb.append("  Type            : ").append(header.getType()).append('\n');
        } catch (Exception e) {
            sb.append("  (parse error: ").append(e.getMessage()).append(")\n");
        }
        sb.append('\n');
    }

    private static void appendIp(StringBuilder sb, Packet packet) {
        if (packet.contains(IpV4Packet.class)) {
            sb.append("=== IPv4 ===\n");
            try {
                IpV4Packet.IpV4Header header = packet.get(IpV4Packet.class).getHeader();
                sb.append("  Source      : ").append(header.getSrcAddr().getHostAddress()).append('\n');
                sb.append("  Destination : ").append(header.getDstAddr().getHostAddress()).append('\n');
                sb.append("  Protocol    : ").append(header.getProtocol()).append('\n');
                sb.append("  TTL         : ").append(header.getTtl()).append('\n');
                sb.append("  Length      : ").append(header.getTotalLength()).append(" bytes\n");
            } catch (Exception e) {
                sb.append("  (parse error)\n");
            }
            sb.append('\n');
            return;
        }
        if (packet.contains(IpV6Packet.class)) {
            sb.append("=== IPv6 ===\n");
            try {
                IpV6Packet.IpV6Header header = packet.get(IpV6Packet.class).getHeader();
                sb.append("  Source      : ").append(header.getSrcAddr().getHostAddress()).append('\n');
                sb.append("  Destination : ").append(header.getDstAddr().getHostAddress()).append('\n');
                sb.append("  Next Header : ").append(header.getNextHeader()).append('\n');
                sb.append("  Hop Limit   : ").append(header.getHopLimit()).append('\n');
                sb.append("  Length      : ").append(header.getPayloadLength()).append(" bytes\n");
            } catch (Exception e) {
                sb.append("  (parse error)\n");
            }
            sb.append('\n');
        }
    }

    private static void appendTransport(StringBuilder sb, Packet packet, String tcpFlags) {
        if (packet.contains(TcpPacket.class)) {
            sb.append("=== TCP ===\n");
            try {
                TcpPacket.TcpHeader header = packet.get(TcpPacket.class).getHeader();
                sb.append("  Source Port      : ").append(header.getSrcPort()).append('\n');
                sb.append("  Destination Port : ").append(header.getDstPort()).append('\n');
                sb.append("  Sequence Number  : ").append(header.getSequenceNumber()).append('\n');
                sb.append("  Ack Number       : ").append(header.getAcknowledgmentNumber()).append('\n');
                sb.append("  Flags            : ").append(tcpFlags).append('\n');
                sb.append("  Window Size      : ").append(header.getWindow()).append('\n');
            } catch (Exception e) {
                sb.append("  (parse error)\n");
            }
            sb.append('\n');
            return;
        }
        if (packet.contains(UdpPacket.class)) {
            sb.append("=== UDP ===\n");
            try {
                UdpPacket.UdpHeader header = packet.get(UdpPacket.class).getHeader();
                sb.append("  Source Port      : ").append(header.getSrcPort()).append('\n');
                sb.append("  Destination Port : ").append(header.getDstPort()).append('\n');
                sb.append("  Length           : ").append(header.getLength()).append('\n');
            } catch (Exception e) {
                sb.append("  (parse error)\n");
            }
            sb.append('\n');
            return;
        }
        if (packet.contains(IcmpV4CommonPacket.class)) {
            sb.append("=== ICMP ===\n");
            try {
                IcmpV4CommonPacket.IcmpV4CommonHeader header =
                        packet.get(IcmpV4CommonPacket.class).getHeader();
                sb.append("  Type    : ").append(header.getType()).append('\n');
                sb.append("  Code    : ").append(header.getCode()).append('\n');
            } catch (Exception e) {
                sb.append("  (parse error)\n");
            }
            sb.append('\n');
        }
    }

    private static void appendHex(StringBuilder sb, Packet packet) {
        sb.append("=== Hex Payload ===\n");
        try {
            byte[] raw = packet.getRawData();
            sb.append(HexFormatter.format(raw));
        } catch (Exception e) {
            sb.append("(unable to read raw data)\n");
        }
    }
}
