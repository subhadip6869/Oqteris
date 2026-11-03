package org.originsd.oqteris.ui.ribbon;

import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.originsd.oqteris.ui.icon.AppIcons;

/**
 * Factory for creating standardized ribbon controls adhering to Oqteris desktop design guidelines.
 * Supports large vertical buttons, small horizontal buttons, columns, split buttons, and tooltips.
 */
public final class RibbonControlFactory {

    private RibbonControlFactory() {}

    /**
     * Creates a large vertical button with an icon on top and descriptive text below.
     */
    public static Button createLargeButton(String text, Ikon ikon, String tooltipText, EventHandler<ActionEvent> onAction) {
        Button btn = new Button(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 20));
        }
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setTextAlignment(TextAlignment.CENTER);
        btn.setAlignment(Pos.CENTER);
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.getStyleClass().add("ribbon-large-btn");
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a large vertical toggle button with an icon on top and text below.
     */
    public static ToggleButton createLargeToggleButton(String text, Ikon ikon, String tooltipText,
                                                      ToggleGroup group, boolean selected,
                                                      EventHandler<ActionEvent> onAction) {
        ToggleButton btn = new ToggleButton(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 20));
        }
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setTextAlignment(TextAlignment.CENTER);
        btn.setAlignment(Pos.CENTER);
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.getStyleClass().add("ribbon-large-btn");
        if (group != null) {
            btn.setToggleGroup(group);
        }
        btn.setSelected(selected);
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a compact horizontal button with an icon on the left and label on the right.
     */
    public static Button createSmallButton(String text, Ikon ikon, String tooltipText, EventHandler<ActionEvent> onAction) {
        Button btn = new Button(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 16));
        }
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.getStyleClass().add("ribbon-btn");
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a compact horizontal toggle button.
     */
    public static ToggleButton createSmallToggleButton(String text, Ikon ikon, String tooltipText,
                                                      ToggleGroup group, boolean selected,
                                                      EventHandler<ActionEvent> onAction) {
        ToggleButton btn = new ToggleButton(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 16));
        }
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.getStyleClass().add("ribbon-btn");
        if (group != null) {
            btn.setToggleGroup(group);
        }
        btn.setSelected(selected);
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates an icon-only square button with tooltip.
     */
    public static Button createIconButton(Ikon ikon, String tooltipText, EventHandler<ActionEvent> onAction) {
        Button btn = new Button();
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 16));
        }
        btn.setAlignment(Pos.CENTER);
        btn.getStyleClass().add("ribbon-icon-btn");
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates an icon-only square toggle button with tooltip.
     */
    public static ToggleButton createIconToggleButton(Ikon ikon, String tooltipText,
                                                      ToggleGroup group, boolean selected,
                                                      EventHandler<ActionEvent> onAction) {
        ToggleButton btn = new ToggleButton();
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, 16));
        }
        btn.setAlignment(Pos.CENTER);
        btn.getStyleClass().add("ribbon-icon-btn");
        if (group != null) {
            btn.setToggleGroup(group);
        }
        btn.setSelected(selected);
        attachTooltip(btn, tooltipText);
        if (onAction != null) {
            btn.setOnAction(onAction);
        }
        return btn;
    }

    /**
     * Creates a menu button with dropdown choices.
     */
    public static MenuButton createMenuButton(String text, Ikon ikon, String tooltipText, boolean large, MenuItem... items) {
        MenuButton btn = new MenuButton(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, large ? 20 : 16));
        }
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        if (large) {
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setTextAlignment(TextAlignment.CENTER);
            btn.setAlignment(Pos.CENTER);
            btn.getStyleClass().add("ribbon-large-menu-btn");
        } else {
            btn.setContentDisplay(ContentDisplay.LEFT);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.getStyleClass().add("ribbon-menu-btn");
        }
        if (items != null) {
            btn.getItems().addAll(items);
        }
        attachTooltip(btn, tooltipText);
        return btn;
    }

    /**
     * Creates a split menu button where the primary button performs default action and arrow opens dropdown.
     */
    public static SplitMenuButton createSplitMenuButton(String text, Ikon ikon, String tooltipText,
                                                        EventHandler<ActionEvent> defaultAction,
                                                        boolean large, MenuItem... items) {
        SplitMenuButton btn = new SplitMenuButton();
        btn.setText(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, large ? 20 : 16));
        }
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        if (large) {
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setTextAlignment(TextAlignment.CENTER);
            btn.setAlignment(Pos.CENTER);
            btn.getStyleClass().add("ribbon-large-split-btn");
        } else {
            btn.setContentDisplay(ContentDisplay.LEFT);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.getStyleClass().add("ribbon-split-btn");
        }
        if (defaultAction != null) {
            btn.setOnAction(defaultAction);
        }
        if (items != null) {
            btn.getItems().addAll(items);
        }
        attachTooltip(btn, tooltipText);
        return btn;
    }

    /**
     * Creates a clearly disabled button with an explanation tooltip.
     */
    public static Button createDisabledButton(String text, Ikon ikon, String tooltipReason, boolean large) {
        Button btn = new Button(text);
        if (ikon != null) {
            btn.setGraphic(AppIcons.of(ikon, large ? 20 : 16));
        }
        btn.setWrapText(false);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.setContentDisplay(large ? ContentDisplay.TOP : ContentDisplay.LEFT);
        btn.setTextAlignment(large ? TextAlignment.CENTER : TextAlignment.LEFT);
        btn.setAlignment(large ? Pos.CENTER : Pos.CENTER_LEFT);
        btn.getStyleClass().add(large ? "ribbon-large-btn" : "ribbon-btn");
        btn.setDisable(true);
        attachTooltip(btn, tooltipReason);
        return btn;
    }

    /**
     * Creates a two-row column for vertical grouping of compact controls.
     */
    public static Column createColumn(Node... nodes) {
        Column col = new Column();
        col.getStyleClass().add("ribbon-column");
        col.setAlignment(Pos.CENTER_LEFT);
        col.setSpacing(3);
        col.setFillWidth(true);
        col.setMinWidth(Region.USE_PREF_SIZE);
        if (nodes != null) {
            for (Node n : nodes) {
                if (n instanceof Region r) {
                    r.setMaxWidth(Double.MAX_VALUE);
                    r.setMinWidth(Region.USE_PREF_SIZE);
                }
                col.getChildren().add(n);
            }
        }
        return col;
    }

    /**
     * Creates a RibbonGroup with the given caption title and child nodes.
     */
    public static RibbonGroup createGroup(String title, Node... nodes) {
        RibbonGroup group = new RibbonGroup();
        group.setTitle(title);
        group.getStyleClass().add("ribbon-group");
        group.setMinWidth(Region.USE_PREF_SIZE);
        if (nodes != null) {
            group.getNodes().addAll(nodes);
        }
        return group;
    }

    public static void attachTooltip(Control control, String text) {
        if (control == null || text == null || text.isBlank()) return;
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(200));
        control.setTooltip(tip);
    }
}