package com.packet.util;

import com.packet.model.PacketInfo;

/**
 * Live display filter — matches user text against packet fields.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code tcp} — protocol contains TCP</li>
 *   <li>{@code port 443} — source or destination port is 443</li>
 *   <li>{@code ip 192.168.} — source or destination IP contains prefix</li>
 * </ul>
 */
public final class PacketFilter {

    private PacketFilter() {
    }

    public static boolean matches(PacketInfo packet, String filterText) {
        if (packet == null) {
            return false;
        }
        if (filterText == null || filterText.isBlank()) {
            return true;
        }

        String query = filterText.trim().toLowerCase();

        if ("tcp".equals(query)) {
            return "TCP".equalsIgnoreCase(packet.getProtocol());
        }
        if ("udp".equals(query)) {
            return "UDP".equalsIgnoreCase(packet.getProtocol());
        }
        if ("icmp".equals(query)) {
            return "ICMP".equalsIgnoreCase(packet.getProtocol());
        }
        if ("ipv4".equals(query)) {
            return "IPv4".equalsIgnoreCase(packet.getIpVersion());
        }
        if ("ipv6".equals(query)) {
            return "IPv6".equalsIgnoreCase(packet.getIpVersion());
        }

        if (query.startsWith("port ")) {
            String port = query.substring(5).trim();
            return port.equals(packet.getSourcePort()) || port.equals(packet.getDestinationPort());
        }

        if (query.startsWith("ip ")) {
            String ipPart = query.substring(3).trim();
            return containsIgnoreCase(packet.getSourceIp(), ipPart)
                    || containsIgnoreCase(packet.getDestinationIp(), ipPart);
        }

        return containsIgnoreCase(packet.getProtocol(), query)
                || containsIgnoreCase(packet.getSourceIp(), query)
                || containsIgnoreCase(packet.getDestinationIp(), query)
                || containsIgnoreCase(packet.getSourcePort(), query)
                || containsIgnoreCase(packet.getDestinationPort(), query)
                || containsIgnoreCase(packet.getInfo(), query)
                || containsIgnoreCase(packet.getTcpFlags(), query);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return haystack.toLowerCase().contains(needle);
    }
}
