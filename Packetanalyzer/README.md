# LAN Packet Analyzer

A beginner-friendly **desktop network packet analyzer** built with **Java 17**, **JavaFX**, and **Pcap4J**. Capture live LAN traffic, inspect packets in real time, filter results, view protocol details, and export captures to CSV — similar to a lightweight Wireshark.

![Main window](docs/screenshots/main-window.png)
*Screenshot placeholder — add your own capture to `docs/screenshots/main-window.png`*

---

## Features

| Feature | Description |
|---------|-------------|
| **Live capture** | Capture TCP, UDP, and ICMP packets from any network interface |
| **Real-time table** | Packet number, timestamp, IPs, ports, protocol, length, TCP flags, summary |
| **Dark UI** | Professional network-analyzer theme with protocol-based row colors |
| **Packet details** | Ethernet, IPv4/IPv6, TCP/UDP/ICMP headers, and hex payload dump |
| **Live filtering** | Filter table while typing (`tcp`, `port 443`, `ip 192.168`, etc.) |
| **Statistics** | Total / TCP / UDP / IPv4 / IPv6 counts, packets/sec, capture timer |
| **Save capture** | Save all captured packets to `captures/capture_yyyyMMdd_HHmmss.csv` |
| **Export CSV** | Export visible (filtered) packets to a user-chosen location via FileChooser |
| **Auto-save** | Optionally append every packet to CSV + log files during capture |

---

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 17 | Core language |
| JavaFX 17 | Desktop GUI (TableView, SplitPane, FileChooser) |
| Maven | Build and dependency management |
| Pcap4J 1.8.2 | Java API for live packet capture |
| Npcap | Native packet capture driver (Windows) |
| IntelliJ IDEA | Recommended IDE |

---

## Prerequisites

### Required

1. **JDK 17** or newer  
2. **Maven 3.6+**  
3. **[Npcap](https://npcap.com/)** installed on Windows  
   - During install, enable **"Install Npcap in WinPcap API-compatible Mode"**
4. **Administrator privileges** may be required to capture packets on some interfaces

### Optional

- IntelliJ IDEA with JavaFX support (run configuration included)

---

## Quick Start

### 1. Clone or open the project

```bash
cd Packetanalyzer
```

### 2. Build

```bash
mvn compile
```

### 3. Run

**IntelliJ IDEA**

- Run configuration: **Packet Analyzer**
- Main class: `com.packet.Main`

**Maven**

```bash
mvn javafx:run
```

**Command line**

```bash
java --module-path target/javafx-modules --add-modules javafx.controls,javafx.fxml -cp target/classes;target/dependency/* com.packet.Main
```

### 4. Capture packets

1. Select a **network interface** (Wi-Fi or Ethernet)
2. Click **Start Capture**
3. Generate traffic (open a browser, run `ping google.com`)
4. Click a row to view **packet details** in the bottom panel
5. Click **Stop Capture** when done

![Packet details panel](docs/screenshots/packet-details.png)
*Screenshot placeholder — add to `docs/screenshots/packet-details.png`*

---

## UI Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Toolbar: Interface | Start | Stop | Clear | Save | Export | Filter │
├─────────────────────────────────────────────────────────────────┤
│ Stats: Total | TCP | UDP | IPv4 | IPv6 | Rate | Timer           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                    Packet Table (TableView)                       │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                    Packet Details (TextArea)                    │
├─────────────────────────────────────────────────────────────────┤
│ Status: ● Capturing | Interface | Current file                  │
└─────────────────────────────────────────────────────────────────┘
```

### Row colors

| Traffic | Color |
|---------|-------|
| TCP | Green tint |
| UDP | Blue tint |
| IPv6 | Purple tint |
| ICMP | Orange tint |

---

## Filter Cheat Sheet

Type in the **Filter** field to narrow the table instantly.

| Filter | Matches |
|--------|---------|
| `tcp` | TCP packets only |
| `udp` | UDP packets only |
| `icmp` | ICMP packets only |
| `ipv4` | IPv4 packets only |
| `ipv6` | IPv6 packets only |
| `port 443` | Source or destination port 443 |
| `port 53` | DNS traffic |
| `ip 192.168` | IP contains `192.168` (source or destination) |
| `192.168.1.1` | Any field containing that text |
| `SYN` | TCP flags or info containing SYN |

Filters apply to the **display only** — all packets remain in memory unless you clear them.

---

## Saving & Exporting

### Save Capture

- Saves **all packets in memory** to the `captures/` folder
- Filename: `capture_yyyyMMdd_HHmmss.csv`
- Includes column headers

### Export CSV

- Opens a **file save dialog** — you choose location and name
- Exports **visible (filtered)** packets only
- Useful for sharing a subset (e.g. only HTTPS after filtering `port 443`)

### Auto-save

- Check **"Auto-save to captures/"** before starting capture
- Each packet is appended to:
  - `captures/capture_*.csv` — structured data
  - `captures/capture_*.log` — human-readable log lines
- Files are closed automatically when capture stops

| Action | What it saves | When |
|--------|---------------|------|
| Auto-save | Every packet | Continuously during capture |
| Save Capture | All packets in memory | On button click |
| Export CSV | Filtered visible packets | On button click, user picks path |

The `captures/` folder is created automatically and is listed in `.gitignore`.

---

## Project Structure

```
src/main/java/com/packet/
├── Main.java                      # Application entry point
├── controller/
│   └── MainController.java        # UI wiring, capture lifecycle, stats, files
├── view/
│   ├── PacketAnalyzerApp.java     # JavaFX Application
│   ├── MainView.java              # Layout and styling hooks
│   └── MainViewContext.java       # UI control references
├── capture/
│   ├── PacketCaptureService.java  # Background capture thread
│   ├── NetworkInterfaceCatalog.java
│   └── PacketDetailsPrinter.java  # Console debug output
├── parser/
│   ├── PacketParser.java          # Raw packet → PacketInfo
│   └── PacketDetailFormatter.java # Details panel text + hex dump
├── model/
│   ├── PacketInfo.java            # Table row model (JavaFX properties)
│   └── CaptureStatistics.java     # Thread-safe live counters
├── storage/
│   ├── CaptureFileManager.java    # CSV/log save and export
│   └── CaptureSession.java        # Auto-save session metadata
└── util/
    ├── PacketFilter.java          # Display filter logic
    └── HexFormatter.java          # Hex dump formatting

src/main/resources/css/
└── style.css                      # Dark theme
```

---

## Architecture

### Packet flow

```
Network (Npcap)
      ↓
PacketCaptureService  (background thread)
      ↓
PacketParser          → PacketInfo + detail text
      ↓
MainController        → Platform.runLater → TableView
                      → CaptureStatistics
                      → CaptureFileManager (optional auto-save)
```

### Design principles

- **Capture thread** never touches JavaFX controls directly
- **Parser** has no UI dependencies — easier to test
- **FilteredList** separates full capture data from displayed rows
- **File writes** use `synchronized` blocks for thread-safe auto-save
- **try-with-resources** ensures writers are always closed

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| No interfaces listed | Install Npcap with WinPcap API compatibility |
| Capture fails immediately | Run as Administrator |
| No packets appear | Select the active interface (Wi-Fi/Ethernet); generate traffic |
| JavaFX won't start | Ensure `target/javafx-modules` exists — run `mvn compile` first |
| Build fails on Linux/Mac | Change JavaFX classifier in `pom.xml` from `win` to `linux` or `mac` |

---

## Development

### Compile

```bash
mvn compile
```

### Main class

```
com.packet.Main
```

### Key dependencies (see `pom.xml`)

- `org.openjfx:javafx-controls` (platform classifier)
- `org.pcap4j:pcap4j-core`
- `org.pcap4j:pcap4j-packetfactory-static`

---

## Roadmap

- [ ] Unit tests for `PacketParser` and `PacketFilter`
- [ ] PCAP binary save/load
- [ ] Cross-platform JavaFX classifiers (Linux, macOS)
- [ ] Deep packet search across payload
- [ ] Pause auto-scroll toggle

---

## License

This project is for educational and portfolio use. See repository owner for license terms.

---

## Author

Built as a hands-on project to learn network programming, multithreading, JavaFX, and packet analysis fundamentals.
