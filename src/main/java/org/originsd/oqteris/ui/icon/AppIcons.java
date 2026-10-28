package org.originsd.oqteris.ui.icon;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Utility factory for creating consistent, high-DPI resolution-independent vector icons.
 * Ribbon buttons use vertical layout (icon on top, short label below) to match Office UI.
 */
public final class AppIcons {
    private AppIcons() {}

    public static FontIcon of(Ikon ikon) {
        return of(ikon, 16);
    }

    public static FontIcon of(Ikon ikon, int size) {
        FontIcon icon = FontIcon.of(ikon, size);
        icon.getStyleClass().add("app-icon");
        return icon;
    }

    public static FontIcon of(Ikon ikon, int size, Color color) {
        FontIcon icon = FontIcon.of(ikon, size, color);
        icon.getStyleClass().add("app-icon");
        return icon;
    }

    /**
     * Creates a horizontal ribbon button (icon on left, full label on right).
     * Tooltip shows the full text with instant hover response.
     */
    public static Button button(String text, Ikon ikon, EventHandler<ActionEvent> onAction) {
        Button btn = new Button();
        if (ikon != null) {
            btn.setGraphic(of(ikon, 15));
        }
        btn.setText(text);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add("ribbon-btn");
        if (text != null && !text.isBlank()) {
            Tooltip tip = new Tooltip(text);
            tip.setShowDelay(javafx.util.Duration.millis(250));
            btn.setTooltip(tip);
        }
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a vertical button (large icon on top, label below) for prominent actions (e.g. Paste, New Slide).
     */
    public static Button verticalButton(String text, Ikon ikon, EventHandler<ActionEvent> onAction) {
        Button btn = new Button();
        if (ikon != null) {
            btn.setGraphic(of(ikon, 20));
        }
        btn.setText(text);
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setTextAlignment(TextAlignment.CENTER);
        btn.setAlignment(Pos.CENTER);
        btn.getStyleClass().add("ribbon-vertical-btn");
        if (text != null && !text.isBlank()) {
            Tooltip tip = new Tooltip(text);
            tip.setShowDelay(javafx.util.Duration.millis(250));
            btn.setTooltip(tip);
        }
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a compact icon-only button (no text) with a tooltip.
     */
    public static Button iconButton(Ikon ikon, String tooltip, EventHandler<ActionEvent> onAction) {
        Button btn = new Button();
        btn.setGraphic(of(ikon, 16));
        btn.getStyleClass().add("ribbon-icon-btn");
        if (tooltip != null && !tooltip.isBlank()) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setShowDelay(javafx.util.Duration.millis(250));
            btn.setTooltip(tip);
        }
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    public static ToggleButton toggleButton(Ikon ikon, String tooltip) {
        ToggleButton btn = new ToggleButton();
        btn.setGraphic(of(ikon, 16));
        btn.getStyleClass().add("ribbon-icon-btn");
        if (tooltip != null && !tooltip.isBlank()) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setShowDelay(javafx.util.Duration.millis(250));
            btn.setTooltip(tip);
        }
        return btn;
    }

    public static Label badge(String text, Ikon ikon, String styleClass) {
        Label lbl = new Label(text);
        if (ikon != null) {
            lbl.setGraphic(of(ikon, 14));
        }
        lbl.setAlignment(Pos.CENTER);
        if (styleClass != null) {
            lbl.getStyleClass().add(styleClass);
        }
        return lbl;
    }

    /** Truncates a label string to a short, ribbon-friendly form. */
    private static String shortenLabel(String text) {
        if (text == null) return "";
        // Common abbreviations
        text = text.replace(" (Ctrl+B)", "").replace(" (Ctrl+I)", "").replace(" (Ctrl+U)", "");
        text = text.replace("...", "").trim();
        if (text.length() <= 9) return text;
        // Take first word if multi-word
        int space = text.indexOf(' ');
        if (space > 0 && space <= 9) return text.substring(0, space);
        return text.substring(0, 8);
    }
}
