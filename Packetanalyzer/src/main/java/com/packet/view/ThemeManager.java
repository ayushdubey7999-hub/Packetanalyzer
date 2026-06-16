package com.packet.view;

import com.packet.model.AppTheme;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Applies light/dark stylesheets to JavaFX scenes and dialogs at runtime.
 */
public final class ThemeManager {

    private static final String LIGHT_STYLESHEET = "/css/light-theme.css";
    private static final String DARK_STYLESHEET = "/css/dark-theme.css";

    private ThemeManager() {
    }

    public static void apply(Scene scene, AppTheme theme) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().setAll(resolve(theme));
    }

    public static void apply(DialogPane dialogPane, AppTheme theme) {
        if (dialogPane == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(resolve(theme));
    }

    private static String resolve(AppTheme theme) {
        String path = theme == AppTheme.DARK ? DARK_STYLESHEET : LIGHT_STYLESHEET;
        return ThemeManager.class.getResource(path).toExternalForm();
    }
}
