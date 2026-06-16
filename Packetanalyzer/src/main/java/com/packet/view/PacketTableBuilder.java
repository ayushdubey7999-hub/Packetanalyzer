package com.packet.view;

import com.packet.model.PacketInfo;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

/**
 * Sortable, responsive packet table — Info column receives the largest share of width.
 *
 * <p>Blank space after Info is caused by {@code UNCONSTRAINED_RESIZE_POLICY}, which keeps
 * columns at fixed widths while unused table area shows as empty filler. {@code CONSTRAINED_RESIZE_POLICY}
 * scales columns proportionally to {@code prefWidth}, filling the full table width.
 */
public final class PacketTableBuilder {

    private PacketTableBuilder() {
    }

    public static TableView<PacketInfo> build(ObservableList<PacketInfo> rows) {
        TableView<PacketInfo> table = new TableView<>(rows);
        table.getStyleClass().add("packet-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addLongColumn(table, "Packet No", "packetNumber", 68, 80);
        addStringColumn(table, "Timestamp", "timestamp", 96, 110);
        addStringColumn(table, "Source IP", "sourceIp", 110, 130);
        addStringColumn(table, "Destination IP", "destinationIp", 110, 130);
        addStringColumn(table, "Source Port", "sourcePort", 68, 80);
        addStringColumn(table, "Destination Port", "destinationPort", 68, 80);
        addStringColumn(table, "Protocol", "protocol", 64, 72);
        addIntColumn(table, "Length", "length", 60, 68);
        addStringColumn(table, "TCP Flags", "tcpFlags", 80, 96);

        TableColumn<PacketInfo, String> infoColumn = new TableColumn<>("Info");
        infoColumn.setCellValueFactory(new PropertyValueFactory<>("info"));
        infoColumn.setComparator(String::compareToIgnoreCase);
        infoColumn.setMinWidth(120);
        infoColumn.setPrefWidth(600);
        infoColumn.setMaxWidth(Double.MAX_VALUE);
        table.getColumns().add(infoColumn);

        Label placeholder = new Label("No packets — start capture or adjust the filter");
        placeholder.getStyleClass().add("table-placeholder");
        table.setPlaceholder(placeholder);
        table.setRowFactory(protocolRowFactory());

        return table;
    }

    private static Callback<TableView<PacketInfo>, TableRow<PacketInfo>> protocolRowFactory() {
        return tv ->
                new TableRow<>() {
                    @Override
                    protected void updateItem(PacketInfo item, boolean empty) {
                        super.updateItem(item, empty);
                        getStyleClass().removeAll("table-row-tcp", "table-row-udp", "table-row-icmp");
                        if (!empty && item != null) {
                            String style = item.rowStyleClass();
                            if (!style.isEmpty()) {
                                getStyleClass().add(style);
                            }
                        }
                    }
                };
    }

    private static void addStringColumn(
            TableView<PacketInfo> table, String title, String property, double minWidth, double prefWidth) {
        TableColumn<PacketInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setComparator(String::compareToIgnoreCase);
        column.setMinWidth(minWidth);
        column.setPrefWidth(prefWidth);
        column.setResizable(true);
        table.getColumns().add(column);
    }

    private static void addLongColumn(
            TableView<PacketInfo> table, String title, String property, double minWidth, double prefWidth) {
        TableColumn<PacketInfo, Long> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setComparator(Long::compare);
        column.setMinWidth(minWidth);
        column.setPrefWidth(prefWidth);
        column.setResizable(true);
        table.getColumns().add(column);
    }

    private static void addIntColumn(
            TableView<PacketInfo> table, String title, String property, double minWidth, double prefWidth) {
        TableColumn<PacketInfo, Integer> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setComparator(Integer::compare);
        column.setMinWidth(minWidth);
        column.setPrefWidth(prefWidth);
        column.setResizable(true);
        table.getColumns().add(column);
    }
}
