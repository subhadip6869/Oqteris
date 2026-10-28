package org.oqteris.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

import java.net.URL;
import java.util.prefs.Preferences;

/**
 * Manages the active application theme (Light / Dark) and applies CSS to JavaFX scenes.
 */
public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String PREF_DARK_THEME = "dark_theme_enabled";

    private final BooleanProperty darkThemeProperty = new SimpleBooleanProperty(false);
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);

    private ThemeManager() {
        boolean dark = prefs.getBoolean(PREF_DARK_THEME, false);
        darkThemeProperty.set(dark);
        darkThemeProperty.addListener((obs, oldVal, newVal) -> {
            prefs.putBoolean(PREF_DARK_THEME, newVal);
        });
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public boolean isDarkTheme() {
        return darkThemeProperty.get();
    }

    public void setDarkTheme(boolean dark) {
        darkThemeProperty.set(dark);
    }

    public void toggleTheme() {
        setDarkTheme(!isDarkTheme());
    }

    public BooleanProperty darkThemeProperty() {
        return darkThemeProperty;
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        URL commonCss = getClass().getResource("/styles/common.css");
        if (commonCss != null) {
            scene.getStylesheets().add(commonCss.toExternalForm());
        }

        String themeFile = isDarkTheme() ? "/styles/theme-dark.css" : "/styles/theme-light.css";
        URL themeCss = getClass().getResource(themeFile);
        if (themeCss != null) {
            scene.getStylesheets().add(themeCss.toExternalForm());
        }
    }
}
