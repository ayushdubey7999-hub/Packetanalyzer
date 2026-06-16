package com.packet.model;

/** UI color theme persisted in application settings. */
public enum AppTheme {

    LIGHT("Light"),
    DARK("Dark");

    private final String displayName;

    AppTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AppTheme fromDisplayName(String name) {
        if (name == null) {
            return LIGHT;
        }
        for (AppTheme theme : values()) {
            if (theme.displayName.equalsIgnoreCase(name) || theme.name().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return LIGHT;
    }
}
