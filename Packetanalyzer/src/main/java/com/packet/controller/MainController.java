package com.packet.controller;

import com.packet.capture.NetworkInterfaceCatalog;
import com.packet.capture.PacketCaptureService;
import com.packet.capture.PacketDetailsPrinter;
import com.packet.model.CaptureStatistics;
import com.packet.model.PacketInfo;
import com.packet.model.SettingsModel;
import com.packet.parser.PacketParser;
import com.packet.settings.SettingsManager;
import com.packet.storage.CaptureFileManager;
import com.packet.storage.CaptureSession;
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
    private final PacketParser packetParser = new PacketParser();
    private final CaptureFileManager fileManager = new CaptureFileManager();
    private final CaptureStatistics statistics = new CaptureStatistics();
    private final SettingsManager settingsManager = new SettingsManager();

    private MainViewContext ui;
    private Scene scene;
    private Timeline statsTimeline;
    private Instant captureStartedAt;
    private long packetsAtLastTick;

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
        ui.clearButton.setOnAction(e -> clearPackets());
        ui.exportButton.setOnAction(e -> exportVisiblePackets());
        ui.settingsButton.setOnAction(e -> openSettings());
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
