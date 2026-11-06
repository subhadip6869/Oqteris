package org.originsd.oqteris.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.originsd.oqteris.config.UserSettings;
import org.originsd.oqteris.config.UserSettingsService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Manages active application theme (LIGHT, DARK, SYSTEM) and typography scaling
 * according to the OriginSD Master Design System & Theme Engine specification.
 */
public class ThemeManager {

    public enum Theme {
        LIGHT("/styles/light-theme.css"),
        DARK("/styles/dark-theme.css"),
        SYSTEM("/styles/light-theme.css");

        private final String cssPath;

        Theme(String cssPath) {
            this.cssPath = cssPath;
        }

        public String getCssPath() {
            return cssPath;
        }
    }

    private static final String MASTER_THEME_CSS = "/styles/oqteris-theme.css";
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final BooleanProperty darkThemeProperty = new SimpleBooleanProperty(false);
    private final Set<Scene> activeScenes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<DialogPane> activeDialogPanes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<com.pixelduke.control.Ribbon> activeRibbons = Collections.newSetFromMap(new WeakHashMap<>());

    private Theme currentTheme = Theme.SYSTEM;
    private int fontSizeDelta = 0;

    private ThemeManager() {
        // Load initial theme from UserSettingsService
        try {
            UserSettings settings = UserSettingsService.getInstance().getSettings();
            if (settings != null) {
                this.fontSizeDelta = settings.getFontSizeDelta();
                applyThemeMode(settings.getTheme(), false);
            }
        } catch (Exception e) {
            this.currentTheme = Theme.SYSTEM;
            this.darkThemeProperty.set(isOsDarkTheme());
        }

        darkThemeProperty.addListener((obs, oldVal, newVal) -> {
            refreshAll();
        });
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public boolean isDarkTheme() {
        return darkThemeProperty.get();
    }

    public void setDarkTheme(boolean dark) {
        setTheme(dark ? "DARK" : "LIGHT");
    }

    public void toggleTheme() {
        setDarkTheme(!isDarkTheme());
    }

    public BooleanProperty darkThemeProperty() {
        return darkThemeProperty;
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public int getFontSizeDelta() {
        return fontSizeDelta;
    }

    public void setFontSizeDelta(int delta) {
        this.fontSizeDelta = Math.clamp(delta, -4, 12);
        try {
            UserSettingsService.getInstance().update(s -> s.setFontSizeDelta(this.fontSizeDelta));
        } catch (Exception ignored) {
        }
        refreshAll();
    }

    public void setTheme(String themeStr) {
        applyThemeMode(themeStr, true);
    }

    private void applyThemeMode(String themeStr, boolean persist) {
        if (themeStr == null || themeStr.isBlank()) {
            themeStr = "SYSTEM";
        }
        try {
            currentTheme = Theme.valueOf(themeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            currentTheme = Theme.SYSTEM;
        }

        boolean activeDark;
        if (currentTheme == Theme.SYSTEM) {
            activeDark = isOsDarkTheme();
        } else {
            activeDark = (currentTheme == Theme.DARK);
        }

        darkThemeProperty.set(activeDark);

        if (persist) {
            try {
                final String savedTheme = currentTheme.name();
                UserSettingsService.getInstance().update(s -> s.setTheme(savedTheme));
            } catch (Exception ignored) {
            }
        }

        refreshAll();
    }

    public void refreshAll() {
        for (Scene s : activeScenes) {
            applyTheme(s);
        }
        for (DialogPane dp : activeDialogPanes) {
            applyTheme(dp);
        }
        for (com.pixelduke.control.Ribbon r : activeRibbons) {
            applyTheme(r);
        }
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        activeScenes.add(scene);

        boolean isDark = isDarkTheme();
        String paletteFile = isDark ? "/styles/dark-theme.css" : "/styles/light-theme.css";

        scene.getStylesheets().clear();

        URL paletteUrl = getClass().getResource(paletteFile);
        if (paletteUrl != null) {
            scene.getStylesheets().add(paletteUrl.toExternalForm());
        }

        URL masterUrl = getClass().getResource(MASTER_THEME_CSS);
        if (masterUrl != null) {
            scene.getStylesheets().add(masterUrl.toExternalForm());
        }

        int baseSize = Math.clamp(13 + fontSizeDelta, 10, 24);
        if (scene.getRoot() != null) {
            scene.getRoot().setStyle("-fx-font-size: " + baseSize + "px;");
        }
    }

    public void applyTheme(Dialog<?> dialog) {
        if (dialog == null) return;
        applyTheme(dialog.getDialogPane());
    }

    public void applyTheme(DialogPane pane) {
        if (pane == null) return;
        activeDialogPanes.add(pane);

        boolean isDark = isDarkTheme();
        String paletteFile = isDark ? "/styles/dark-theme.css" : "/styles/light-theme.css";

        pane.getStylesheets().clear();

        URL paletteUrl = getClass().getResource(paletteFile);
        if (paletteUrl != null) {
            pane.getStylesheets().add(paletteUrl.toExternalForm());
        }

        URL masterUrl = getClass().getResource(MASTER_THEME_CSS);
        if (masterUrl != null) {
            pane.getStylesheets().add(masterUrl.toExternalForm());
        }

        int baseSize = Math.clamp(13 + fontSizeDelta, 10, 24);
        pane.setStyle("-fx-font-size: " + baseSize + "px;");
    }

    public void applyTheme(com.pixelduke.control.Ribbon ribbon) {
        if (ribbon == null) return;
        activeRibbons.add(ribbon);

        boolean isDark = isDarkTheme();
        String paletteFile = isDark ? "/styles/dark-theme.css" : "/styles/light-theme.css";

        ribbon.getStylesheets().clear();

        URL paletteUrl = getClass().getResource(paletteFile);
        if (paletteUrl != null) {
            ribbon.getStylesheets().add(paletteUrl.toExternalForm());
        }

        URL masterUrl = getClass().getResource(MASTER_THEME_CSS);
        if (masterUrl != null) {
            ribbon.getStylesheets().add(masterUrl.toExternalForm());
        }

        String bg = isDark ? "#0b0f19" : "#EDF1F8";
        ribbon.setStyle("-fx-background-color: " + bg + " !important; -fx-background: " + bg + " !important;");
    }

    /**
     * Attempts to query OS system dark mode on Windows / macOS / Linux.
     */
    private static boolean isOsDarkTheme() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                });
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme") && line.contains("0x0")) {
                            return true; // 0 means Dark theme
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
