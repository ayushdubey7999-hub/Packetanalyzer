package com.packet.model;

/**
 * Optional metadata describing where session data originated.
 *
 * <p>Reserved for future PCAP/PCAPNG import provenance tracking.
 */
public final class SessionSource {

    private String type = "memory";
    private String path;
    private String interfaceName;

    public SessionSource() {
    }

    public SessionSource(String type, String path, String interfaceName) {
        this.type = type != null ? type : "memory";
        this.path = path;
        this.interfaceName = interfaceName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type : "memory";
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
}
