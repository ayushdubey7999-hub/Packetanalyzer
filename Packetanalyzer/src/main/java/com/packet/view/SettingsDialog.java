package com.packet.view;

import com.packet.model.AppTheme;
import com.packet.model.SettingsModel;
import com.packet.settings.SettingsManager;
import java.util.List;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.pcap4j.core.PcapNetworkInterface;

/**
 * Settings editor — edits a working copy, previews theme live, persists on Save.
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    public static Optional<SettingsModel> show(
            Stage owner,
            Scene scene,
            SettingsManager settingsManager,
            List<PcapNetworkInterface> interfaces,
            Runnable onSaveCapture) {
        SettingsModel working = settingsManager.getSettings().copy();
        AppTheme themeBeforeDialog = settingsManager.getSettings().getTheme();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Application preferences");
        dialog.initOwner(owner);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType resetType = new ButtonType("Reset Defaults", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType, resetType);

        CheckBox autoSaveCheck = new CheckBox("Auto-save packets to captures/ during live capture");
        autoSaveCheck.getStyleClass().add("settings-checkbox");
        autoSaveCheck.setSelected(working.isAutoSave());

        CheckBox autoScrollCheck = new CheckBox("Auto-scroll packet table to newest packet");
        autoScrollCheck.getStyleClass().add("settings-checkbox");
        autoScrollCheck.setSelected(working.isAutoScroll());

        ComboBox<AppTheme> themeCombo = new ComboBox<>();
        themeCombo.getItems().setAll(AppTheme.values());
        themeCombo.setValue(working.getTheme());
        themeCombo.setMaxWidth(Double.MAX_VALUE);
        themeCombo.setCellFactory(lv -> themeCell());
        themeCombo.setButtonCell(themeCell());

        ComboBox<String> interfaceCombo = new ComboBox<>();
        interfaceCombo.setMaxWidth(Double.MAX_VALUE);
        interfaceCombo.getItems().add("");
        interfaces.stream().map(PcapNetworkInterface::getName).forEach(interfaceCombo.getItems()::add);
        interfaceCombo.setValue(
                working.getDefaultInterfaceName() != null ? working.getDefaultInterfaceName() : "");

        TextField bufferField = new TextField(String.valueOf(working.getCaptureBufferSize()));
        bufferField.getStyleClass().add("settings-field");

        Label bufferHint =
                new Label(
                        "Range: "
                                + SettingsModel.MIN_BUFFER_SIZE
                                + " – "
                                + SettingsModel.MAX_BUFFER_SIZE
                                + " (max table rows and capture snap length)");
        bufferHint.getStyleClass().add("settings-hint");
        bufferHint.setWrapText(true);

        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("settings-error");
        validationLabel.setWrapText(true);

        GridPane grid = buildGrid(
                autoSaveCheck, autoScrollCheck, themeCombo, interfaceCombo, bufferField);

        VBox content = new VBox(10, grid, bufferHint, validationLabel);
        if (onSaveCapture != null) {
            Button saveCaptureBtn = new Button("Save current capture to captures/");
            saveCaptureBtn.getStyleClass().addAll("button", "primary");
            saveCaptureBtn.setMaxWidth(Double.MAX_VALUE);
            saveCaptureBtn.setOnAction(e -> onSaveCapture.run());
            content.getChildren().add(saveCaptureBtn);
        }

        dialog.getDialogPane().setContent(content);
        ThemeManager.apply(dialog.getDialogPane(), working.getTheme());

        themeCombo.valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (n != null) {
                                ThemeManager.apply(scene, n);
                                ThemeManager.apply(dialog.getDialogPane(), n);
                            }
                        });

        Button resetBtn = (Button) dialog.getDialogPane().lookupButton(resetType);
        resetBtn.addEventFilter(
                ActionEvent.ACTION,
                e -> {
                    e.consume();
                    applyToForm(SettingsModel.defaults(), autoSaveCheck, autoScrollCheck, themeCombo, interfaceCombo, bufferField);
                    validationLabel.setText("");
                    ThemeManager.apply(scene, AppTheme.LIGHT);
                    ThemeManager.apply(dialog.getDialogPane(), AppTheme.LIGHT);
                });

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(
                ActionEvent.ACTION,
                e -> {
                    String error = readAndValidate(autoSaveCheck, autoScrollCheck, themeCombo, interfaceCombo, bufferField, working);
                    if (error != null) {
                        e.consume();
                        validationLabel.setText(error);
                    } else {
                        validationLabel.setText("");
                    }
                });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) {
            ThemeManager.apply(scene, themeBeforeDialog);
            return Optional.empty();
        }

        try {
            settingsManager.save(working);
            return Optional.of(working.copy());
        } catch (IllegalArgumentException ex) {
            ThemeManager.apply(scene, themeBeforeDialog);
            return Optional.empty();
        }
    }

    private static String readAndValidate(
            CheckBox autoSaveCheck,
            CheckBox autoScrollCheck,
            ComboBox<AppTheme> themeCombo,
            ComboBox<String> interfaceCombo,
            TextField bufferField,
            SettingsModel target) {
        target.setAutoSave(autoSaveCheck.isSelected());
        target.setAutoScroll(autoScrollCheck.isSelected());
        target.setTheme(themeCombo.getValue() != null ? themeCombo.getValue() : AppTheme.LIGHT);
        target.setDefaultInterfaceName(interfaceCombo.getValue() != null ? interfaceCombo.getValue() : "");

        try {
            target.setCaptureBufferSize(Integer.parseInt(bufferField.getText().trim()));
        } catch (NumberFormatException ex) {
            return "Buffer size must be a whole number.";
        }

        return target.validate();
    }

    private static void applyToForm(
            SettingsModel model,
            CheckBox autoSaveCheck,
            CheckBox autoScrollCheck,
            ComboBox<AppTheme> themeCombo,
            ComboBox<String> interfaceCombo,
            TextField bufferField) {
        autoSaveCheck.setSelected(model.isAutoSave());
        autoScrollCheck.setSelected(model.isAutoScroll());
        themeCombo.setValue(model.getTheme());
        interfaceCombo.setValue(model.getDefaultInterfaceName());
        bufferField.setText(String.valueOf(model.getCaptureBufferSize()));
    }

    private static GridPane buildGrid(
            CheckBox autoSaveCheck,
            CheckBox autoScrollCheck,
            ComboBox<AppTheme> themeCombo,
            ComboBox<String> interfaceCombo,
            TextField bufferField) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("settings-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(4, 0, 0, 0));

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(150);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        addRow(grid, 0, "Auto Save", autoSaveCheck);
        addRow(grid, 1, "Auto Scroll", autoScrollCheck);
        addRow(grid, 2, "Theme", themeCombo);
        addRow(grid, 3, "Default Interface", interfaceCombo);
        addRow(grid, 4, "Capture Buffer Size", bufferField);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("settings-field-label");
        GridPane.setHgrow(control, Priority.ALWAYS);
        GridPane.setValignment(label, VPos.CENTER);
        GridPane.setHalignment(label, HPos.RIGHT);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }

    private static ListCell<AppTheme> themeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AppTheme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        };
    }
}
