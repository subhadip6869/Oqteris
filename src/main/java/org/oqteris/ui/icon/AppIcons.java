package org.oqteris.ui.icon;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Utility factory for creating consistent, high-DPI resolution-independent vector icons.
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

    public static Button button(String text, Ikon ikon, EventHandler<ActionEvent> onAction) {
        Button btn = new Button(text);
        if (ikon != null) {
            btn.setGraphic(of(ikon, 16));
        }
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    public static Button iconButton(Ikon ikon, String tooltip, EventHandler<ActionEvent> onAction) {
        Button btn = new Button();
        btn.setGraphic(of(ikon, 16));
        if (tooltip != null && !tooltip.isBlank()) {
            btn.setTooltip(new Tooltip(tooltip));
        }
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    public static ToggleButton toggleButton(Ikon ikon, String tooltip) {
        ToggleButton btn = new ToggleButton();
        btn.setGraphic(of(ikon, 16));
        if (tooltip != null && !tooltip.isBlank()) {
            btn.setTooltip(new Tooltip(tooltip));
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
}
