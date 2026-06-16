package com.packet.capture;

import java.util.List;
import java.util.Optional;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;

/**
 * Lists capture-capable network interfaces via Pcap4J / Npcap.
 */
public final class NetworkInterfaceCatalog {

    private NetworkInterfaceCatalog() {
    }

    public static List<PcapNetworkInterface> listAll() throws PcapNativeException {
        return Pcaps.findAllDevs();
    }

    public static Optional<PcapNetworkInterface> findByName(List<PcapNetworkInterface> interfaces, String name) {
        if (name == null || name.isBlank() || interfaces == null) {
            return Optional.empty();
        }
        return interfaces.stream().filter(nif -> name.equals(nif.getName())).findFirst();
    }

    public static String describe(PcapNetworkInterface nif) {
        StringBuilder sb = new StringBuilder();
        sb.append(nif.getName());
        String description = nif.getDescription();
        if (description != null && !description.isBlank()) {
            sb.append(" — ").append(description);
        }
        nif.getAddresses().stream()
                .findFirst()
                .ifPresent(addr -> sb.append(" (").append(addr.getAddress()).append(')'));
        return sb.toString();
    }
}
