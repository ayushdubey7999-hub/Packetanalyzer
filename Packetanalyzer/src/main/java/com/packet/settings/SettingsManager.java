package com.packet.settings;

import com.packet.model.AppTheme;
import com.packet.model.SettingsModel;
import java.util.prefs.Preferences;

/**
 * Loads and persists {@link SettingsModel} using the Java Preferences API.
 *
 * <p>Preferences are stored per user under node {@code com/packet/analyzer}.
 */
public final class SettingsManager {

    private static final String PREF_NODE = "com/packet/analyzer";

    private static final String KEY_AUTO_SAVE = "autoSave";
    private static final String KEY_AUTO_SCROLL = "autoScroll";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DEFAULT_INTERFACE = "defaultInterface";
    private static final String KEY_BUFFER_SIZE = "captureBufferSize";

    private final Preferences preferences;
    private SettingsModel current;

    public SettingsManager() {
        preferences = Preferences.userRoot().node(PREF_NODE);
        current = load();
    }

    public SettingsModel getSettings() {
        return current;
    }

    /** Loads settings from disk or returns defaults when missing/invalid. */
    public SettingsModel load() {
        SettingsModel model = SettingsModel.defaults();
        model.setAutoSave(preferences.getBoolean(KEY_AUTO_SAVE, model.isAutoSave()));
        model.setAutoScroll(preferences.getBoolean(KEY_AUTO_SCROLL, model.isAutoScroll()));
        model.setTheme(AppTheme.fromDisplayName(preferences.get(KEY_THEME, model.getTheme().name())));
        model.setDefaultInterfaceName(preferences.get(KEY_DEFAULT_INTERFACE, model.getDefaultInterfaceName()));
        model.setCaptureBufferSize(preferences.getInt(KEY_BUFFER_SIZE, model.getCaptureBufferSize()));

        if (model.validate() != null) {
            model.setCaptureBufferSize(SettingsModel.DEFAULT_BUFFER_SIZE);
        }

        current = model;
        return current.copy();
    }

    /**
     * Validates and persists settings, then updates the in-memory copy.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void save(SettingsModel model) {
        String error = model.validate();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        preferences.putBoolean(KEY_AUTO_SAVE, model.isAutoSave());
        preferences.putBoolean(KEY_AUTO_SCROLL, model.isAutoScroll());
        preferences.put(KEY_THEME, model.getTheme().name());
        preferences.put(KEY_DEFAULT_INTERFACE, model.getDefaultInterfaceName());
        preferences.putInt(KEY_BUFFER_SIZE, model.getCaptureBufferSize());

        try {
            preferences.flush();
        } catch (Exception ignored) {
            // Preferences flush may fail in restricted environments; in-memory state still applies.
        }

        current = model.copy();
    }

    public void resetToDefaults() {
        save(SettingsModel.defaults());
    }
}
