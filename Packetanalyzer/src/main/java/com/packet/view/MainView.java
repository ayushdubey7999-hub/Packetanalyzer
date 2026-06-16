package com.packet.view;

import com.packet.capture.NetworkInterfaceCatalog;
import com.packet.model.PacketInfo;
import com.packet.util.AppInfo;
import com.packet.view.components.UiComponents;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import com.packet.view.components.PacketDetailsPanel;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.pcap4j.core.PcapNetworkInterface;

/**
 * Professional network-analyzer layout — visuals only, no capture logic.
 */
public final class MainView {

    private MainView() {
    }

    public static BorderPane build() {
        ObservableList<PacketInfo> masterPackets = FXCollections.observableArrayList();
        FilteredList<PacketInfo> filteredPackets = new FilteredList<>(masterPackets, p -> true);

        ComboBox<PcapNetworkInterface> interfaceCombo = createInterfaceCombo();
        TextField filterField = new TextField();
        filterField.setPromptText("Filter — tcp, udp, icmp, ipv4, port 443, ip 192.168…");
        filterField.getStyleClass().add("filter-field");

        Button filterClearButton = UiComponents.toolbarButton("Clear");
        filterClearButton.getStyleClass().add("filter-clear");
        Label filterMatchLabel = UiComponents.statusText("Showing 0 of 0");
        filterMatchLabel.getStyleClass().add("filter-match-label");

        Button startButton = UiComponents.primaryButton("Start");
        Button stopButton = UiComponents.dangerButton("Stop");
        stopButton.setDisable(true);
        Button clearButton = UiComponents.toolbarButton("Clear");
        Button exportButton = UiComponents.toolbarButton("Export");
        Button settingsButton = UiComponents.toolbarButton("Settings");

        TableView<PacketInfo> packetTable = PacketTableBuilder.build(filteredPackets);

        PacketDetailsPanel detailsPanel = new PacketDetailsPanel();

        Label totalLabel = UiComponents.statValue("0");
        Label tcpLabel = UiComponents.statValue("0");
        Label udpLabel = UiComponents.statValue("0");
        Label ipv4Label = UiComponents.statValue("0");
        Label ipv6Label = UiComponents.statValue("0");
        Label rateLabel = UiComponents.statValue("0.0/s");
        Label timerLabel = UiComponents.statValue("00:00:00");
        rateLabel.getStyleClass().add("stat-secondary-value");
        timerLabel.getStyleClass().add("stat-secondary-value");

        Region captureIndicator = new Region();
        captureIndicator.getStyleClass().add("status-indicator");

        Label captureStatusLabel = UiComponents.statusText("Ready");
        Label interfaceStatusLabel = UiComponents.statusText("Interface: —");
        Label packetCountLabel = UiComponents.statusText("0 / 0 packets");
        Label fileStatusLabel = UiComponents.statusText("File: —");
        Label versionLabel = UiComponents.statusText("v" + AppInfo.VERSION);
        versionLabel.getStyleClass().add("version-label");

        // Toolbar
        interfaceCombo.setMaxWidth(360);
        HBox toolbar =
                new HBox(
                        8,
                        UiComponents.toolbarLabel("Interface"),
                        interfaceCombo,
                        UiComponents.horizontalSpacer(),
                        startButton,
                        stopButton,
                        clearButton,
                        exportButton,
                        settingsButton);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Statistics cards
        HBox rateRow = new HBox(6, UiComponents.toolbarLabel("Rate"), rateLabel);
        HBox timerRow = new HBox(6, UiComponents.toolbarLabel("Timer"), timerLabel);
        rateRow.setAlignment(Pos.CENTER_LEFT);
        timerRow.setAlignment(Pos.CENTER_LEFT);
        VBox secondaryStats = new VBox(4, rateRow, timerRow);
        secondaryStats.getStyleClass().add("stat-secondary");
        HBox.setHgrow(secondaryStats, Priority.ALWAYS);

        HBox statsRow =
                new HBox(
                        12,
                        UiComponents.statCard("Total", totalLabel, "stat-card-total"),
                        UiComponents.statCard("TCP", tcpLabel, "stat-card-tcp"),
                        UiComponents.statCard("UDP", udpLabel, "stat-card-udp"),
                        UiComponents.statCard("IPv4", ipv4Label, "stat-card-ipv4"),
                        UiComponents.statCard("IPv6", ipv6Label, "stat-card-ipv6"),
                        secondaryStats);
        statsRow.getStyleClass().add("stats-row");
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // Filter bar
        HBox.setHgrow(filterField, Priority.ALWAYS);
        HBox filterBar =
                new HBox(
                        8,
                        UiComponents.toolbarLabel("Search"),
                        filterField,
                        filterClearButton,
                        UiComponents.horizontalSpacer(),
                        filterMatchLabel);
        filterBar.getStyleClass().add("filter-bar");
        filterBar.setAlignment(Pos.CENTER_LEFT);

        SplitPane centerSplit = new SplitPane(packetTable, detailsPanel);
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        centerSplit.setDividerPositions(0.65);
        centerSplit.getStyleClass().add("main-split");

        // Status bar
        Region sep1 = statusSeparator();
        Region sep2 = statusSeparator();
        Region sep3 = statusSeparator();

        HBox statusBar =
                new HBox(
                        14,
                        captureIndicator,
                        captureStatusLabel,
                        sep1,
                        interfaceStatusLabel,
                        sep2,
                        packetCountLabel,
                        sep3,
                        fileStatusLabel,
                        UiComponents.horizontalSpacer(),
                        versionLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        MainViewContext ctx =
                new MainViewContext(
                        interfaceCombo,
                        filterField,
                        filterClearButton,
                        filterMatchLabel,
                        startButton,
                        stopButton,
                        clearButton,
                        exportButton,
                        settingsButton,
                        packetTable,
                        masterPackets,
                        filteredPackets,
                        centerSplit,
                        detailsPanel,
                        totalLabel,
                        tcpLabel,
                        udpLabel,
                        ipv4Label,
                        ipv6Label,
                        rateLabel,
                        timerLabel,
                        captureIndicator,
                        captureStatusLabel,
                        interfaceStatusLabel,
                        packetCountLabel,
                        fileStatusLabel,
                        versionLabel);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(new VBox(toolbar, statsRow, filterBar));
        root.setCenter(centerSplit);
        root.setBottom(statusBar);
        root.getProperties().put("viewContext", ctx);

        return root;
    }

    public static MainViewContext contextFrom(BorderPane root) {
        return (MainViewContext) root.getProperties().get("viewContext");
    }

    private static Region statusSeparator() {
        Region sep = new Region();
        sep.getStyleClass().add("status-separator");
        return sep;
    }

    private static ComboBox<PcapNetworkInterface> createInterfaceCombo() {
        ComboBox<PcapNetworkInterface> combo = new ComboBox<>();
        combo.getStyleClass().add("interface-combo");
        combo.setPromptText("Select network interface…");
        combo.setCellFactory(lv -> interfaceCell());
        combo.setButtonCell(interfaceCell());
        return combo;
    }

    public static void loadInterfaces(ComboBox<PcapNetworkInterface> combo, String preferredInterfaceName) {
        try {
            var interfaces = NetworkInterfaceCatalog.listAll();
            combo.getItems().setAll(interfaces);
            NetworkInterfaceCatalog.findByName(interfaces, preferredInterfaceName)
                    .ifPresentOrElse(
                            nif -> combo.getSelectionModel().select(nif),
                            () -> {
                                if (!combo.getItems().isEmpty()) {
                                    combo.getSelectionModel().selectFirst();
                                }
                            });
        } catch (Exception e) {
            combo.getItems().clear();
        }
    }

    private static ListCell<PcapNetworkInterface> interfaceCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PcapNetworkInterface item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : NetworkInterfaceCatalog.describe(item));
            }
        };
    }
}
