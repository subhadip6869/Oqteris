package org.originsd.oqteris.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/**
 * Manages the active application theme (Light / Dark) and applies Fluent JMetro styling and CSS.
 */
public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String PREF_DARK_THEME = "dark_theme_enabled";

    private final BooleanProperty darkThemeProperty = new SimpleBooleanProperty(false);
    private final Preferences prefs;
    private final JMetro jMetro = new JMetro();
    private final Set<Scene> activeScenes = Collections.newSetFromMap(new WeakHashMap<>());

    private ThemeManager() {
        Preferences p = null;
        try {
            p = Preferences.userNodeForPackage(ThemeManager.class);
        } catch (Throwable ignored) {
            // Gracefully handle environments where Windows Registry or Java Preferences are inaccessible
        }
        this.prefs = p;

        boolean dark = false;
        if (prefs != null) {
            try {
                dark = prefs.getBoolean(PREF_DARK_THEME, false);
            } catch (Throwable ignored) {
            }
        }
        darkThemeProperty.set(dark);
        darkThemeProperty.addListener((obs, oldVal, newVal) -> {
            if (prefs != null) {
                try {
                    prefs.putBoolean(PREF_DARK_THEME, newVal);
                } catch (Throwable ignored) {
                }
            }
            for (Scene s : activeScenes) {
                applyTheme(s);
            }
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
        activeScenes.add(scene);

        Style style = isDarkTheme() ? Style.DARK : Style.LIGHT;
        jMetro.setStyle(style);
        try {
            jMetro.setScene(scene);
        } catch (Exception ignored) {
        }

        URL commonCss = getClass().getResource("/styles/common.css");
        if (commonCss != null && !scene.getStylesheets().contains(commonCss.toExternalForm())) {
            scene.getStylesheets().add(commonCss.toExternalForm());
        }

        String themeFile = isDarkTheme() ? "/styles/theme-dark.css" : "/styles/theme-light.css";
        URL themeCss = getClass().getResource(themeFile);
        if (themeCss != null) {
            scene.getStylesheets().removeIf(s -> s != null && (s.contains("theme-light.css") || s.contains("theme-dark.css")));
            scene.getStylesheets().add(themeCss.toExternalForm());
        }
    }
}
