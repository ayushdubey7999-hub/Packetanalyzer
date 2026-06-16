package com.packet.model;

import com.packet.util.MathUtil;

/**
 * Application settings — plain data object with no UI or persistence logic.
 */
public final class SettingsModel {

    public static final int MIN_BUFFER_SIZE = 100;
    public static final int MAX_BUFFER_SIZE = 100_000;
    public static final int DEFAULT_BUFFER_SIZE = 10_000;

    private boolean autoSave;
    private boolean autoScroll;
    private AppTheme theme;
    private String defaultInterfaceName;
    private int captureBufferSize;

    public SettingsModel() {
        applyDefaults();
    }

    public static SettingsModel defaults() {
        return new SettingsModel();
    }

    public void applyDefaults() {
        autoSave = false;
        autoScroll = true;
        theme = AppTheme.LIGHT;
        defaultInterfaceName = "";
        captureBufferSize = DEFAULT_BUFFER_SIZE;
    }

    public SettingsModel copy() {
        SettingsModel copy = new SettingsModel();
        copy.autoSave = autoSave;
        copy.autoScroll = autoScroll;
        copy.theme = theme;
        copy.defaultInterfaceName = defaultInterfaceName;
        copy.captureBufferSize = captureBufferSize;
        return copy;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isAutoScroll() {
        return autoScroll;
    }

    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }

    public AppTheme getTheme() {
        return theme;
    }

    public void setTheme(AppTheme theme) {
        this.theme = theme != null ? theme : AppTheme.LIGHT;
    }

    public String getDefaultInterfaceName() {
        return defaultInterfaceName;
    }

    public void setDefaultInterfaceName(String defaultInterfaceName) {
        this.defaultInterfaceName = defaultInterfaceName != null ? defaultInterfaceName : "";
    }

    public int getCaptureBufferSize() {
        return captureBufferSize;
    }

    public void setCaptureBufferSize(int captureBufferSize) {
        this.captureBufferSize = captureBufferSize;
    }

    /** Pcap snap length derived from buffer size (bytes per captured frame). */
    public int snapLength() {
        return MathUtil.clamp(captureBufferSize, 1_500, 65_536);
    }

    /** Maximum packets retained in the in-memory table. */
    public int maxTableRows() {
        return MathUtil.clamp(captureBufferSize, MIN_BUFFER_SIZE, MAX_BUFFER_SIZE);
    }

    /**
     * @return validation error message, or {@code null} if valid
     */
    public String validate() {
        if (captureBufferSize < MIN_BUFFER_SIZE || captureBufferSize > MAX_BUFFER_SIZE) {
            return "Buffer size must be between " + MIN_BUFFER_SIZE + " and " + MAX_BUFFER_SIZE + ".";
        }
        return null;
    }
}
