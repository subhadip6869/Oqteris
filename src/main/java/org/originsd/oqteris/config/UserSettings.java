package org.originsd.oqteris.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * User-configurable application preferences adhering to the OriginSD Settings Standard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {

    // Appearance & Typography
    private String theme = "SYSTEM"; // "LIGHT", "DARK", "SYSTEM"
    private int fontSizeDelta = 0;   // -4 to +12

    // App-specific preferences
    private String defaultAuthor = System.getProperty("user.name", "Oqteris User");
    private boolean autosaveEnabled = true;
    private int autosaveIntervalMinutes = 2;
    private boolean autoCheckUpdates = true;
    private boolean promptDefaultViewerOnStartup = true;
    private List<String> recentFilePaths = new ArrayList<>();

    public UserSettings() {}

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = (theme != null && !theme.isBlank()) ? theme.toUpperCase() : "SYSTEM";
    }

    public int getFontSizeDelta() {
        return fontSizeDelta;
    }

    public void setFontSizeDelta(int fontSizeDelta) {
        this.fontSizeDelta = Math.clamp(fontSizeDelta, -4, 12);
    }

    public String getDefaultAuthor() {
        return defaultAuthor;
    }

    public void setDefaultAuthor(String defaultAuthor) {
        this.defaultAuthor = defaultAuthor;
    }

    public boolean isAutosaveEnabled() {
        return autosaveEnabled;
    }

    public void setAutosaveEnabled(boolean autosaveEnabled) {
        this.autosaveEnabled = autosaveEnabled;
    }

    public int getAutosaveIntervalMinutes() {
        return autosaveIntervalMinutes;
    }

    public void setAutosaveIntervalMinutes(int autosaveIntervalMinutes) {
        this.autosaveIntervalMinutes = Math.max(1, autosaveIntervalMinutes);
    }

    public boolean isAutoCheckUpdates() {
        return autoCheckUpdates;
    }

    public void setAutoCheckUpdates(boolean autoCheckUpdates) {
        this.autoCheckUpdates = autoCheckUpdates;
    }

    public boolean isPromptDefaultViewerOnStartup() {
        return promptDefaultViewerOnStartup;
    }

    public void setPromptDefaultViewerOnStartup(boolean promptDefaultViewerOnStartup) {
        this.promptDefaultViewerOnStartup = promptDefaultViewerOnStartup;
    }

    public List<String> getRecentFilePaths() {
        if (recentFilePaths == null) {
            recentFilePaths = new ArrayList<>();
        }
        return recentFilePaths;
    }

    public void setRecentFilePaths(List<String> recentFilePaths) {
        this.recentFilePaths = recentFilePaths != null ? recentFilePaths : new ArrayList<>();
    }
}
