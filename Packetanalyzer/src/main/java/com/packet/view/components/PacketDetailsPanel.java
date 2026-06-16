package com.packet.view.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Structured packet details view — renders formatted detail text as section cards.
 */
public final class PacketDetailsPanel extends VBox {

    private final ScrollPane scrollPane;
    private final VBox contentBox;
    private final Label emptyLabel;

    public PacketDetailsPanel() {
        getStyleClass().add("details-panel");

        emptyLabel = new Label("Select a packet row to inspect protocol headers and hex payload.");
        emptyLabel.getStyleClass().add("details-empty");
        emptyLabel.setWrapText(true);

        contentBox = new VBox(10);
        contentBox.getStyleClass().add("details-content");

        scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("details-scroll");

        getChildren().addAll(UiComponents.sectionHeader("Packet Details"), scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        showEmpty();
    }

    public void showDetail(String detailText) {
        contentBox.getChildren().clear();
        if (detailText == null || detailText.isBlank()) {
            showEmpty();
            return;
        }

        String[] sections = detailText.split("(?=^=== )");
        boolean added = false;
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            contentBox.getChildren().add(buildSectionCard(trimmed));
            added = true;
        }

        if (!added) {
            contentBox.getChildren().add(buildPlainCard(detailText));
        }

        scrollPane.setVvalue(0);
    }

    public void clear() {
        showEmpty();
    }

    private void showEmpty() {
        contentBox.getChildren().clear();
        contentBox.getChildren().add(emptyLabel);
    }

    private static VBox buildSectionCard(String section) {
        String title = extractTitle(section);
        String body = extractBody(section);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("detail-section-title", titleCssClass(title));

        VBox card = new VBox(6);
        card.getStyleClass().add("detail-section-card");
        card.getChildren().add(titleLabel);

        if (title.toLowerCase().contains("hex")) {
            TextArea hexArea = new TextArea(body.trim());
            hexArea.setEditable(false);
            hexArea.setWrapText(false);
            hexArea.getStyleClass().add("detail-hex-area");
            hexArea.setPrefRowCount(Math.min(12, Math.max(4, body.split("\n").length)));
            card.getChildren().add(hexArea);
        } else {
            Text bodyText = new Text(body.trim());
            bodyText.getStyleClass().add("detail-section-body");
            TextFlow bodyFlow = new TextFlow(bodyText);
            bodyFlow.setPadding(new Insets(2, 0, 0, 0));
            card.getChildren().add(bodyFlow);
        }

        return card;
    }

    private static VBox buildPlainCard(String text) {
        VBox card = new VBox(6);
        card.getStyleClass().add("detail-section-card");
        Text bodyText = new Text(text.trim());
        bodyText.getStyleClass().add("detail-section-body");
        TextFlow bodyFlow = new TextFlow(bodyText);
        card.getChildren().add(bodyFlow);
        return card;
    }

    private static String extractTitle(String section) {
        if (section.startsWith("=== ") && section.contains(" ===")) {
            int end = section.indexOf(" ===");
            return section.substring(4, end).trim();
        }
        return "Details";
    }

    private static String extractBody(String section) {
        int headerEnd = section.indexOf(" ===");
        if (headerEnd >= 0) {
            return section.substring(headerEnd + 4).trim();
        }
        return section;
    }

    private static String titleCssClass(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("ethernet")) {
            return "title-ethernet";
        }
        if (lower.contains("ipv4")) {
            return "title-ipv4";
        }
        if (lower.contains("ipv6")) {
            return "title-ipv6";
        }
        if (lower.contains("tcp")) {
            return "title-tcp";
        }
        if (lower.contains("udp")) {
            return "title-udp";
        }
        if (lower.contains("icmp")) {
            return "title-icmp";
        }
        if (lower.contains("hex")) {
            return "title-hex";
        }
        return "";
    }
}
