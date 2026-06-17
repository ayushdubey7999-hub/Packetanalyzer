package com.packet.controller;

import com.packet.capture.NetworkInterfaceCatalog;
import com.packet.capture.PacketCaptureService;
import com.packet.capture.PacketDetailsPrinter;
import com.packet.capture.PacketImportService;
import com.packet.model.CaptureStatistics;
import com.packet.model.PacketInfo;
import com.packet.model.SessionMode;
import com.packet.model.SettingsModel;
import com.packet.parser.PacketParser;
import com.packet.settings.SettingsManager;
import com.packet.storage.CaptureFileManager;
import com.packet.storage.CaptureSession;
import com.packet.storage.SessionLoadException;
import com.packet.storage.SessionManager;
import com.packet.util.AppInfo;
import com.packet.util.PacketFilter;
import com.packet.view.MainView;
import com.packet.view.MainViewContext;
import com.packet.view.SettingsDialog;
import com.packet.view.ThemeManager;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.packet.Packet;

/**
 * Coordinates UI, capture, parsing, filtering, statistics, settings, and file I/O.
 */
public final class MainController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Stage stage;
    private final PacketCaptureService captureService = new PacketCaptureService();
    private final PacketImportService importService = new PacketImportService();
    private final PacketParser packetParser = new PacketParser();
    private final CaptureFileManager fileManager = new CaptureFileManager();
    private final SessionManager sessionManager = new SessionManager();
    private final CaptureStatistics statistics = new CaptureStatistics();
    private final SettingsManager settingsManager = new SettingsManager();

    private MainViewContext ui;
    private Scene scene;
    private Timeline statsTimeline;
    private Instant captureStartedAt;
    private long packetsAtLastTick;
    private volatile boolean sessionIoActive;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = MainView.build();
        ui = MainView.contextFrom(root);

        stage.setOnCloseRequest(e -> shutdown());
        stage.setTitle(AppInfo.NAME);
        scene = new Scene(root, 1280, 720);
        stage.setScene(scene);
        applySettings(settingsManager.getSettings());
        stage.show();

        try {
            MainView.loadInterfaces(
                    ui.interfaceCombo, settingsManager.getSettings().getDefaultInterfaceName());
        } catch (Exception e) {
            showError("Could not list network interfaces. Install Npcap and retry.", e);
        }

        wireFilter();
        wireTableSelection();
        wireButtons();
        wireMenu();
        wirePacketCountUpdates();

        ui.interfaceCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateInterfaceStatus(n));

        startStatsTimeline();
        updateInterfaceStatus(ui.interfaceCombo.getSelectionModel().getSelectedItem());
        updatePacketCounts();
        setCaptureStatus("Ready — select an interface and press Start");
    }

    private void applySettings(SettingsModel settings) {
        ThemeManager.apply(scene, settings.getTheme());
    }

    private void wireFilter() {
        ui.filterField.textProperty().addListener((obs, oldText, newText) -> {
            String query = newText == null ? "" : newText;
            ui.filteredPackets.setPredicate(packet -> PacketFilter.matches(packet, query));
            updatePacketCounts();
        });

        ui.filterClearButton.setOnAction(e -> {
            ui.filterField.clear();
            ui.packetTable.getSelectionModel().clearSelection();
        });
    }

    private void wirePacketCountUpdates() {
        ListChangeListener<PacketInfo> listener = change -> updatePacketCounts();
        ui.masterPackets.addListener(listener);
        ui.filteredPackets.addListener(listener);
    }

    private void updatePacketCounts() {
        int visible = ui.filteredPackets.size();
        int total = ui.masterPackets.size();
        ui.packetCountLabel.setText(visible + " / " + total + " packets");
        ui.filterMatchLabel.setText("Showing " + visible + " of " + total);
    }

    private void wireTableSelection() {
        ui.packetTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) {
                ui.detailsPanel.showDetail(newRow.getDetailText());
            }
        });
    }

    private void wireButtons() {
        ui.startButton.setOnAction(e -> startCapture());
        ui.stopButton.setOnAction(e -> stopCapture());
        ui.importButton.setOnAction(e -> importCaptureFile());
        ui.clearButton.setOnAction(e -> clearPackets());
        ui.exportButton.setOnAction(e -> exportVisiblePackets());
        ui.settingsButton.setOnAction(e -> openSettings());
    }

    private void wireMenu() {
        ui.saveSessionMenuItem.setOnAction(e -> saveSession());
        ui.openSessionMenuItem.setOnAction(e -> openSession());
    }

    private void openSettings() {
        List<PcapNetworkInterface> interfaces = List.copyOf(ui.interfaceCombo.getItems());
        SettingsDialog.show(stage, scene, settingsManager, interfaces, this::saveCaptureToFolder)
                .ifPresent(
                        saved -> {
                            applySettings(saved);
                            MainView.loadInterfaces(ui.interfaceCombo, saved.getDefaultInterfaceName());
                            updateInterfaceStatus(ui.interfaceCombo.getSelectionModel().getSelectedItem());
                        });
    }

    private SettingsModel settings() {
        return settingsManager.getSettings();
    }

    private void startCapture() {
        PcapNetworkInterface networkInterface = ui.interfaceCombo.getSelectionModel().getSelectedItem();
        if (networkInterface == null) {
            new Alert(Alert.AlertType.WARNING, "Select a network interface first.").showAndWait();
            return;
        }

        stopCapture();

        statistics.reset();
        captureStartedAt = Instant.now();
        packetsAtLastTick = 0;
        ui.sessionModeLabel.setText(SessionMode.LIVE_CAPTURE);

        if (settings().isAutoSave()) {
            try {
                CaptureSession session =
                        fileManager.startAutoSaveSession(NetworkInterfaceCatalog.describe(networkInterface));
                ui.fileStatusLabel.setText("File: " + shortenPath(session.getCsvPath()));
                setCaptureStatus("Capturing with auto-save…");
            } catch (IOException e) {
                showError("Could not start auto-save session.", e);
            }
        } else {
            ui.fileStatusLabel.setText("File: —");
        }

        ui.startButton.setDisable(true);
        ui.stopButton.setDisable(false);
        ui.captureIndicator.getStyleClass().add("capturing");
        updateInterfaceStatus(networkInterface);
        if (!settings().isAutoSave()) {
            setCaptureStatus("Capturing…");
        }

        captureService.startCapture(
                networkInterface,
                settings().snapLength(),
                (packet, packetNumber) -> onPacketCaptured(packet, packetNumber),
                error ->
                        Platform.runLater(
                                () -> {
                                    showError("Capture stopped due to an error.", error);
                                    stopCapture();
                                }));
    }

    private void onPacketCaptured(Packet packet, long packetNumber) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);

        PacketDetailsPrinter.printPacketDetails(packet, packetParser, packetNumber, timestamp);

        PacketInfo row = packetParser.parse(packet, packetNumber, timestamp);
        if (row == null) {
            return;
        }

        statistics.record(row);

        if (settings().isAutoSave()) {
            try {
                fileManager.appendPacket(row);
            } catch (IOException e) {
                Platform.runLater(
                        () -> {
                            showError("Auto-save failed.", e);
                            fileManager.closeAutoSaveSession();
                            ui.fileStatusLabel.setText("File: —");
                        });
            }
        }

        Platform.runLater(() -> addPacketToTable(row));
    }

    private void importCaptureFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Capture File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Capture files", "*.pcap", "*.pcapng"),
                new FileChooser.ExtensionFilter("PCAP files", "*.pcap"),
                new FileChooser.ExtensionFilter("PCAPNG files", "*.pcapng"));

        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        stopCapture();
        clearPackets();
        ui.sessionModeLabel.setText(SessionMode.IMPORTED_CAPTURE);
        ui.captureStatusLabel.setText("Opening file…");
        ui.fileStatusLabel.setText("File: " + shortenPath(file.getAbsolutePath()));
        ui.importButton.setDisable(true);
        ui.startButton.setDisable(true);
        ui.stopButton.setDisable(true);

        importService.importCapture(
                file.toPath(),
                progress ->
                        Platform.runLater(
                                () -> {
                                    ui.captureStatusLabel.setText(progress.message());
                                    ui.packetCountLabel.setText(progress.packetCount() + " packets imported");
                                }),
                packets -> Platform.runLater(() -> addImportedPackets(packets)),
                summary ->
                        Platform.runLater(
                                () -> {
                                    ui.sessionModeLabel.setText(summary.mode());
                                    ui.captureStatusLabel.setText(
                                            "Imported " + summary.packetCount() + " packets");
                                    ui.importButton.setDisable(false);
                                    ui.startButton.setDisable(false);
                                    ui.stopButton.setDisable(true);
                                }),
                error ->
                        Platform.runLater(
                                () -> {
                                    ui.importButton.setDisable(false);
                                    ui.startButton.setDisable(false);
                                    ui.stopButton.setDisable(true);
                                    showError("Could not import capture file.", error);
                                    setCaptureStatus("Import failed");
                                }));
    }

    private void addImportedPackets(List<PacketInfo> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }

        for (PacketInfo packet : packets) {
            statistics.record(packet);
            ui.masterPackets.add(0, packet);
            while (ui.masterPackets.size() > settings().maxTableRows()) {
                ui.masterPackets.remove(ui.masterPackets.size() - 1);
            }
        }

        refreshLiveStats();
        updatePacketCounts();
        if (settings().isAutoScroll()) {
            ui.packetTable.scrollTo(0);
        }
    }

    private void addPacketToTable(PacketInfo row) {
        int maxRows = settings().maxTableRows();
        ui.masterPackets.add(0, row);
        while (ui.masterPackets.size() > maxRows) {
            ui.masterPackets.remove(ui.masterPackets.size() - 1);
        }
        if (settings().isAutoScroll()) {
            ui.packetTable.scrollTo(0);
        }
    }

    private void stopCapture() {
        captureService.stopCapture();
        fileManager.closeAutoSaveSession();

        ui.startButton.setDisable(false);
        ui.stopButton.setDisable(true);
        ui.captureIndicator.getStyleClass().remove("capturing");
        setCaptureStatus("Stopped");
        ui.importButton.setDisable(false);
    }

    private void shutdown() {
        stopCapture();
        if (statsTimeline != null) {
            statsTimeline.stop();
        }
    }

    private void clearPackets() {
        ui.masterPackets.clear();
        ui.detailsPanel.clear();
        ui.packetTable.getSelectionModel().clearSelection();
        statistics.reset();
        captureStartedAt = null;
        packetsAtLastTick = 0;
        refreshStatLabels(0);
        updatePacketCounts();
        setCaptureStatus("Packets cleared");
        ui.sessionModeLabel.setText(SessionMode.LIVE_CAPTURE);
    }

    private void saveCaptureToFolder() {
        if (ui.masterPackets.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No packets to save.").showAndWait();
            return;
        }
        try {
            Path saved = fileManager.saveAllToCapturesFolder(List.copyOf(ui.masterPackets));
            ui.fileStatusLabel.setText("File: " + shortenPath(saved.toString()));
            setCaptureStatus("Saved " + ui.masterPackets.size() + " packets");
            new Alert(Alert.AlertType.INFORMATION, "Saved to:\n" + saved).showAndWait();
        } catch (IOException e) {
            showError("Could not save capture.", e);
        }
    }

    private void saveSession() {
        if (ui.masterPackets.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No packets to save in this session.").showAndWait();
            return;
        }
        if (sessionIoActive) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Session");
        chooser
                .getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "LAN Packet Analyzer sessions", "*." + SessionManager.FILE_EXTENSION));
        chooser.setInitialFileName(
                "session_" + CaptureSession.timestampForFilename() + "." + SessionManager.FILE_EXTENSION);

        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        Path destination = ensureSessionExtension(file.toPath());
        SessionManager.SaveRequest request =
                new SessionManager.SaveRequest(
                        List.copyOf(ui.masterPackets),
                        statistics,
                        ui.sessionModeLabel.getText(),
                        null);

        setSessionIoActive(true);
        setCaptureStatus("Saving session…");

        Thread saveThread =
                new Thread(
                        () -> {
                            try {
                                sessionManager.save(destination, request);
                                Platform.runLater(
                                        () -> {
                                            ui.fileStatusLabel.setText("File: " + shortenPath(destination.toString()));
                                            setCaptureStatus("Saved " + request.packets().size() + " packets");
                                            new Alert(
                                                            Alert.AlertType.INFORMATION,
                                                            "Session saved to:\n" + destination)
                                                    .showAndWait();
                                        });
                            } catch (IOException e) {
                                Platform.runLater(() -> showError("Could not save session.", e));
                            } finally {
                                Platform.runLater(() -> setSessionIoActive(false));
                            }
                        },
                        "session-save");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void openSession() {
        if (sessionIoActive) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Session");
        chooser
                .getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "LAN Packet Analyzer sessions", "*." + SessionManager.FILE_EXTENSION));

        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        stopCapture();
        clearPacketsForSessionLoad();
        ui.sessionModeLabel.setText(SessionMode.LOADED_SESSION);
        setSessionIoActive(true);
        ui.captureStatusLabel.setText("Opening session…");
        ui.fileStatusLabel.setText("File: " + shortenPath(file.getAbsolutePath()));

        sessionManager.loadAsync(
                file.toPath(),
                progress ->
                        Platform.runLater(
                                () -> {
                                    ui.captureStatusLabel.setText(progress.message());
                                    ui.packetCountLabel.setText(progress.packetCount() + " packets loaded");
                                }),
                batch -> Platform.runLater(() -> addLoadedSessionPackets(batch)),
                session -> Platform.runLater(() -> finishSessionLoad(session, file.toPath())),
                error ->
                        Platform.runLater(
                                () -> {
                                    setSessionIoActive(false);
                                    showSessionError("Could not open session file.", error);
                                    setCaptureStatus("Session load failed");
                                    ui.sessionModeLabel.setText(SessionMode.LIVE_CAPTURE);
                                }));
    }

    private void clearPacketsForSessionLoad() {
        ui.masterPackets.clear();
        ui.detailsPanel.clear();
        ui.packetTable.getSelectionModel().clearSelection();
        statistics.reset();
        captureStartedAt = null;
        packetsAtLastTick = 0;
        refreshStatLabels(0);
        updatePacketCounts();
    }

    private void addLoadedSessionPackets(List<PacketInfo> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }

        int maxRows = settings().maxTableRows();
        for (PacketInfo packet : packets) {
            ui.masterPackets.add(0, packet);
            while (ui.masterPackets.size() > maxRows) {
                ui.masterPackets.remove(ui.masterPackets.size() - 1);
            }
        }
        updatePacketCounts();
        if (settings().isAutoScroll()) {
            ui.packetTable.scrollTo(0);
        }
    }

    private void finishSessionLoad(SessionManager.LoadedSession session, Path path) {
        statistics.restoreFrom(session.statistics());
        packetsAtLastTick = statistics.getTotal();
        captureStartedAt = null;
        ui.sessionModeLabel.setText(SessionMode.LOADED_SESSION);
        ui.rateLabel.setText("0.0/s");
        ui.timerLabel.setText("00:00:00");
        refreshLiveStats();
        updatePacketCounts();
        ui.fileStatusLabel.setText("File: " + shortenPath(path.toString()));
        setCaptureStatus("Loaded " + session.packetCount() + " packets");
        setSessionIoActive(false);
    }

    private void setSessionIoActive(boolean active) {
        sessionIoActive = active;
        ui.saveSessionMenuItem.setDisable(active);
        ui.openSessionMenuItem.setDisable(active);
        ui.importButton.setDisable(active);
        ui.exportButton.setDisable(active);
        if (active) {
            ui.startButton.setDisable(true);
            ui.stopButton.setDisable(true);
            return;
        }
        if (!active) {
            ui.importButton.setDisable(false);
            ui.exportButton.setDisable(false);
        }
        if (captureService.isRunning()) {
            ui.startButton.setDisable(true);
            ui.stopButton.setDisable(false);
        } else {
            ui.startButton.setDisable(false);
            ui.stopButton.setDisable(true);
        }
    }

    private void showSessionError(String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        if (cause instanceof SessionLoadException) {
            alert.setContentText(cause.getMessage());
        } else if (cause != null) {
            alert.setContentText(cause.getMessage() != null ? cause.getMessage() : cause.toString());
        }
        alert.showAndWait();
    }

    private static Path ensureSessionExtension(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith("." + SessionManager.FILE_EXTENSION)) {
            return path.resolveSibling(fileName + "." + SessionManager.FILE_EXTENSION);
        }
        return path;
    }

    private void exportVisiblePackets() {
        List<PacketInfo> visible = List.copyOf(ui.filteredPackets);
        if (visible.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No visible packets to export.").showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("export_" + CaptureSession.timestampForFilename() + ".csv");

        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            fileManager.exportToCsv(file.toPath(), visible);
            setCaptureStatus("Exported " + visible.size() + " packets");
            new Alert(Alert.AlertType.INFORMATION, "Exported to:\n" + file.getAbsolutePath()).showAndWait();
        } catch (IOException e) {
            showError("Export failed.", e);
        }
    }

    private void startStatsTimeline() {
        statsTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refreshLiveStats()));
        statsTimeline.setCycleCount(Timeline.INDEFINITE);
        statsTimeline.play();
    }

    private void refreshLiveStats() {
        long total = statistics.getTotal();
        ui.totalLabel.setText(String.valueOf(total));
        ui.tcpLabel.setText(String.valueOf(statistics.getTcp()));
        ui.udpLabel.setText(String.valueOf(statistics.getUdp()));
        ui.ipv4Label.setText(String.valueOf(statistics.getIpv4()));
        ui.ipv6Label.setText(String.valueOf(statistics.getIpv6()));

        long delta = total - packetsAtLastTick;
        packetsAtLastTick = total;
        ui.rateLabel.setText(String.format("%.1f/s", (double) delta));

        if (captureStartedAt != null) {
            Duration elapsed = Duration.between(captureStartedAt, Instant.now());
            ui.timerLabel.setText(formatDuration(elapsed));
        }
    }

    private void refreshStatLabels(long total) {
        ui.totalLabel.setText(String.valueOf(total));
        ui.tcpLabel.setText("0");
        ui.udpLabel.setText("0");
        ui.ipv4Label.setText("0");
        ui.ipv6Label.setText("0");
        ui.rateLabel.setText("0.0/s");
        ui.timerLabel.setText("00:00:00");
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private void updateInterfaceStatus(PcapNetworkInterface nif) {
        if (nif == null) {
            ui.interfaceStatusLabel.setText("Interface: —");
        } else {
            String desc = NetworkInterfaceCatalog.describe(nif);
            ui.interfaceStatusLabel.setText("Interface: " + shortenPath(desc));
        }
    }

    private void setCaptureStatus(String text) {
        ui.captureStatusLabel.setText(text);
    }

    private static String shortenPath(String path) {
        if (path == null) {
            return "—";
        }
        return path.length() > 48 ? "…" + path.substring(path.length() - 45) : path;
    }

    private static void showError(String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        if (cause != null) {
            alert.setContentText(cause.getMessage() != null ? cause.getMessage() : cause.toString());
        }
        alert.showAndWait();
    }
}
