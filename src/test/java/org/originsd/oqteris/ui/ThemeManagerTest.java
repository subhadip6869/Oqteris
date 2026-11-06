package org.originsd.oqteris.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeManagerTest {

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void testThemeApplication() {
        ThemeManager themeManager = ThemeManager.getInstance();
        assertNotNull(themeManager);

        VBox root = new VBox(new Label("Test"));
        Scene scene = new Scene(root, 400, 300);

        // Apply dark theme
        themeManager.setTheme("DARK");
        themeManager.applyTheme(scene);
        assertTrue(themeManager.isDarkTheme());
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")));
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("oqteris-theme.css")));

        // Apply light theme
        themeManager.setTheme("LIGHT");
        themeManager.applyTheme(scene);
        assertFalse(themeManager.isDarkTheme());
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("light-theme.css")));
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("oqteris-theme.css")));

        // Font scaling
        themeManager.setFontSizeDelta(3);
        themeManager.applyTheme(scene);
        assertEquals(3, themeManager.getFontSizeDelta());
        assertTrue(scene.getRoot().getStyle().contains("-fx-font-size: 16px;"));
    }

    @Test
    public void testRibbonDarkModeStyling() {
        ThemeManager themeManager = ThemeManager.getInstance();
        com.pixelduke.control.Ribbon ribbon = new com.pixelduke.control.Ribbon();
        ribbon.getTabs().add(new com.pixelduke.control.ribbon.RibbonTab("Home"));
        ribbon.getTabs().add(new com.pixelduke.control.ribbon.RibbonTab("View"));

        themeManager.applyTheme(ribbon);

        VBox root = new VBox(ribbon);
        Scene scene = new Scene(root, 800, 600);

        themeManager.setTheme("DARK");
        themeManager.applyTheme(scene);

        assertTrue(ribbon.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")));
        assertTrue(ribbon.getStylesheets().stream().anyMatch(s -> s.contains("oqteris-theme.css")));

        // Force CSS and layout pass
        root.applyCss();
        root.layout();

        javafx.scene.layout.Region tabHeaderBg = (javafx.scene.layout.Region) ribbon.lookup(".tab-header-background");
        javafx.scene.layout.Region tabHeaderArea = (javafx.scene.layout.Region) ribbon.lookup(".tab-header-area");

        assertNotNull(tabHeaderArea);
        assertNotNull(tabHeaderBg);

        // Switch to Light
        themeManager.setTheme("LIGHT");
        assertTrue(ribbon.getStylesheets().stream().anyMatch(s -> s.contains("light-theme.css")));
        assertTrue(ribbon.getStylesheets().stream().anyMatch(s -> s.contains("oqteris-theme.css")));
    }
}
