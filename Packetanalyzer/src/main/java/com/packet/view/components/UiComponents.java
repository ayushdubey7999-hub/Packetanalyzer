package com.packet.view.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Reusable UI factories — keeps layout code DRY; all visual styling lives in CSS.
 */
public final class UiComponents {

    private UiComponents() {
    }

    public static Label toolbarLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("toolbar-label");
        return label;
    }

    public static Button toolbarButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("button");
        return button;
    }

    public static Button primaryButton(String text) {
        Button button = toolbarButton(text);
        button.getStyleClass().add("primary");
        return button;
    }

    public static Button dangerButton(String text) {
        Button button = toolbarButton(text);
        button.getStyleClass().add("danger");
        return button;
    }

    public static Label sectionHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    public static Label statusText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-text");
        return label;
    }

    /** Flexible spacer for toolbars and status bars. */
    public static Region horizontalSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * Statistics card: title on top, large value below.
     *
     * @param cssVariant optional modifier, e.g. {@code stat-card-tcp}
     */
    public static VBox statCard(String title, Label valueLabel, String cssVariant) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-card-title");

        valueLabel.getStyleClass().add("stat-card-value");

        VBox card = new VBox(4, titleLabel, valueLabel);
        card.getStyleClass().add("stat-card");
        if (cssVariant != null && !cssVariant.isBlank()) {
            card.getStyleClass().add(cssVariant);
        }
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    public static Label statValue(String initial) {
        Label label = new Label(initial);
        label.setMinWidth(56);
        return label;
    }
}
