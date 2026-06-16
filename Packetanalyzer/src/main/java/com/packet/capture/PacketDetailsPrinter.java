package com.packet.capture;

import com.packet.model.PacketInfo;
import com.packet.parser.PacketParser;
import org.pcap4j.packet.Packet;

/**
 * Optional console debugging — prints each captured packet.
 */
public final class PacketDetailsPrinter {

    private PacketDetailsPrinter() {
    }

    public static void printPacketDetails(
            Packet packet, PacketParser parser, long packetNumber, String timestamp) {
        if (packet == null) {
            System.out.println("--- Packet #" + packetNumber + " --- (null)");
            return;
        }

        PacketInfo summary = parser.parse(packet, packetNumber, timestamp);
        System.out.println("--- Packet #" + summary.getPacketNumber() + " @ " + summary.getTimestamp() + " ---");
        System.out.println("  " + summary.getInfo());
        System.out.println(summary.getDetailText());
        System.out.println("---------------------------");
    }
}
