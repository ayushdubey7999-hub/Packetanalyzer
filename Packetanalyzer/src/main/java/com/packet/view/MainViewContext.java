package com.packet.view;

import com.packet.model.PacketInfo;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import com.packet.view.components.PacketDetailsPanel;
import org.pcap4j.core.PcapNetworkInterface;

/**
 * Holds references to UI controls for {@link com.packet.controller.MainController}.
 */
public final class MainViewContext {

    public final ComboBox<PcapNetworkInterface> interfaceCombo;
    public final TextField filterField;
    public final Button filterClearButton;
    public final Label filterMatchLabel;

    public final Button startButton;
    public final Button stopButton;
    public final Button clearButton;
    public final Button exportButton;
    public final Button importButton;
    public final Button settingsButton;

    public final TableView<PacketInfo> packetTable;
    public final ObservableList<PacketInfo> masterPackets;
    public final FilteredList<PacketInfo> filteredPackets;
    public final SplitPane centerSplit;

    public final PacketDetailsPanel detailsPanel;

    public final Label totalLabel;
    public final Label tcpLabel;
    public final Label udpLabel;
    public final Label ipv4Label;
    public final Label ipv6Label;
    public final Label rateLabel;
    public final Label timerLabel;

    public final Region captureIndicator;
    public final Label sessionModeLabel;
    public final Label captureStatusLabel;
    public final Label interfaceStatusLabel;
    public final Label packetCountLabel;
    public final Label fileStatusLabel;
    public final Label versionLabel;

    public MainViewContext(
            ComboBox<PcapNetworkInterface> interfaceCombo,
            TextField filterField,
            Button filterClearButton,
            Label filterMatchLabel,
            Button startButton,
            Button stopButton,
            Button clearButton,
            Button exportButton,
            Button importButton,
            Button settingsButton,
            TableView<PacketInfo> packetTable,
            ObservableList<PacketInfo> masterPackets,
            FilteredList<PacketInfo> filteredPackets,
            SplitPane centerSplit,
            PacketDetailsPanel detailsPanel,
            Label totalLabel,
            Label tcpLabel,
            Label udpLabel,
            Label ipv4Label,
            Label ipv6Label,
            Label rateLabel,
            Label timerLabel,
            Region captureIndicator,
            Label sessionModeLabel,
            Label captureStatusLabel,
            Label interfaceStatusLabel,
            Label packetCountLabel,
            Label fileStatusLabel,
            Label versionLabel) {
        this.interfaceCombo = interfaceCombo;
        this.filterField = filterField;
        this.filterClearButton = filterClearButton;
        this.filterMatchLabel = filterMatchLabel;
        this.startButton = startButton;
        this.stopButton = stopButton;
        this.clearButton = clearButton;
        this.exportButton = exportButton;
        this.importButton = importButton;
        this.settingsButton = settingsButton;
        this.packetTable = packetTable;
        this.masterPackets = masterPackets;
        this.filteredPackets = filteredPackets;
        this.centerSplit = centerSplit;
        this.detailsPanel = detailsPanel;
        this.totalLabel = totalLabel;
        this.tcpLabel = tcpLabel;
        this.udpLabel = udpLabel;
        this.ipv4Label = ipv4Label;
        this.ipv6Label = ipv6Label;
        this.rateLabel = rateLabel;
        this.timerLabel = timerLabel;
        this.captureIndicator = captureIndicator;
        this.sessionModeLabel = sessionModeLabel;
        this.captureStatusLabel = captureStatusLabel;
        this.interfaceStatusLabel = interfaceStatusLabel;
        this.packetCountLabel = packetCountLabel;
        this.fileStatusLabel = fileStatusLabel;
        this.versionLabel = versionLabel;
    }
}
