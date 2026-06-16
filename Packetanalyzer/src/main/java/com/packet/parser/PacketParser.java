package com.packet.parser;

import com.packet.model.PacketInfo;
import java.util.ArrayList;
import java.util.List;
import org.pcap4j.packet.IcmpV4CommonPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.namednumber.IpNumber;

/**
 * Turns a raw Pcap4J {@link Packet} into a {@link PacketInfo} for the UI.
 *
 * <p>No JavaFX — parsing stays separate from the view layer.
 */
public final class PacketParser {

    private static final String UNKNOWN = "-";

    public PacketInfo parse(Packet packet, long packetNumber, String timestamp) {
        if (packet == null) {
            return null;
        }

        int length = packet.length();
        PortPair ports = extractPorts(packet);
        String protocol = detectProtocol(packet);
        String tcpFlags = extractTcpFlags(packet);
        String ipVersion = detectIpVersion(packet);

        String sourceIp = UNKNOWN;
        String destinationIp = UNKNOWN;

        try {
            if (packet.contains(IpV4Packet.class)) {
                IpV4Packet.IpV4Header header = packet.get(IpV4Packet.class).getHeader();
                sourceIp = header.getSrcAddr().getHostAddress();
                destinationIp = header.getDstAddr().getHostAddress();
                ipVersion = "IPv4";
                protocol = resolveTransportProtocol(packet, header.getProtocol());
            } else if (packet.contains(IpV6Packet.class)) {
                IpV6Packet.IpV6Header header = packet.get(IpV6Packet.class).getHeader();
                sourceIp = header.getSrcAddr().getHostAddress();
                destinationIp = header.getDstAddr().getHostAddress();
                ipVersion = "IPv6";
                protocol = resolveTransportProtocol(packet, header.getProtocol());
            } else {
                protocol = "NON-IP";
                ipVersion = "OTHER";
            }
        } catch (Exception ignored) {
            protocol = "NON-IP";
            ipVersion = "OTHER";
        }

        PacketInfo info = new PacketInfo();
        info.setPacketNumber(packetNumber);
        info.setTimestamp(timestamp);
        info.setSourceIp(sourceIp);
        info.setDestinationIp(destinationIp);
        info.setSourcePort(ports.source());
        info.setDestinationPort(ports.destination());
        info.setProtocol(protocol);
        info.setIpVersion(ipVersion);
        info.setLength(length);
        info.setTcpFlags(tcpFlags);
        info.setInfo(buildInfoSummary(protocol, sourceIp, ports.source(), destinationIp, ports.destination(), tcpFlags));
        info.setDetailText(PacketDetailFormatter.format(packet, tcpFlags));
        return info;
    }

    private static String detectIpVersion(Packet packet) {
        if (packet.contains(IpV4Packet.class)) {
            return "IPv4";
        }
        if (packet.contains(IpV6Packet.class)) {
            return "IPv6";
        }
        return "OTHER";
    }

    private static PortPair extractPorts(Packet packet) {
        try {
            if (packet.contains(TcpPacket.class)) {
                TcpPacket.TcpHeader header = packet.get(TcpPacket.class).getHeader();
                return new PortPair(
                        String.valueOf(header.getSrcPort().valueAsInt()),
                        String.valueOf(header.getDstPort().valueAsInt()));
            }
            if (packet.contains(UdpPacket.class)) {
                UdpPacket.UdpHeader header = packet.get(UdpPacket.class).getHeader();
                return new PortPair(
                        String.valueOf(header.getSrcPort().valueAsInt()),
                        String.valueOf(header.getDstPort().valueAsInt()));
            }
        } catch (Exception ignored) {
            // Malformed transport header.
        }
        return new PortPair(UNKNOWN, UNKNOWN);
    }

    private static String detectProtocol(Packet packet) {
        if (packet.contains(TcpPacket.class)) {
            return "TCP";
        }
        if (packet.contains(UdpPacket.class)) {
            return "UDP";
        }
        if (packet.contains(IcmpV4CommonPacket.class)) {
            return "ICMP";
        }
        return UNKNOWN;
    }

    private static String extractTcpFlags(Packet packet) {
        if (!packet.contains(TcpPacket.class)) {
            return UNKNOWN;
        }
        try {
            TcpPacket.TcpHeader header = packet.get(TcpPacket.class).getHeader();
            List<String> flags = new ArrayList<>();
            if (Boolean.TRUE.equals(header.getUrg())) {
                flags.add("URG");
            }
            if (Boolean.TRUE.equals(header.getAck())) {
                flags.add("ACK");
            }
            if (Boolean.TRUE.equals(header.getPsh())) {
                flags.add("PSH");
            }
            if (Boolean.TRUE.equals(header.getRst())) {
                flags.add("RST");
            }
            if (Boolean.TRUE.equals(header.getSyn())) {
                flags.add("SYN");
            }
            if (Boolean.TRUE.equals(header.getFin())) {
                flags.add("FIN");
            }
            return flags.isEmpty() ? UNKNOWN : String.join(", ", flags);
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    private static String resolveTransportProtocol(Packet packet, IpNumber ipProtocol) {
        if (packet.contains(TcpPacket.class) || ipProtocol.equals(IpNumber.TCP)) {
            return "TCP";
        }
        if (packet.contains(UdpPacket.class) || ipProtocol.equals(IpNumber.UDP)) {
            return "UDP";
        }
        if (packet.contains(IcmpV4CommonPacket.class) || ipProtocol.equals(IpNumber.ICMPV4)) {
            return "ICMP";
        }
        return ipProtocol.name();
    }

    private static String buildInfoSummary(
            String protocol,
            String sourceIp,
            String sourcePort,
            String destinationIp,
            String destinationPort,
            String tcpFlags) {
        String endpoints = sourceIp + ":" + sourcePort + " → " + destinationIp + ":" + destinationPort;
        if ("TCP".equals(protocol) && !UNKNOWN.equals(tcpFlags)) {
            return protocol + " " + endpoints + " [" + tcpFlags + "]";
        }
        return protocol + " " + endpoints;
    }

    private record PortPair(String source, String destination) {
    }
}
