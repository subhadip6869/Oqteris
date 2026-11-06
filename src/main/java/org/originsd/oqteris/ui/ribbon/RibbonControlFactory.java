package org.originsd.oqteris.ui.ribbon;

import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
     * Computes a robust minimum/preferred width for a large vertical ribbon button,
     * ensuring adequate breathing room so that labels are never clipped or truncated with ellipsis.
     */
    public static double computeRequiredLargeButtonWidth(String text, boolean hasArrow) {
        if (text == null || text.isBlank()) {
            return hasArrow ? 66.0 : 56.0;
        }
        Font font = Font.font("Segoe UI", 10.5);
        double maxLineWidth = 0.0;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                Text t = new Text(trimmed);
                t.setFont(font);
                double w = t.getLayoutBounds().getWidth();
                if (w > maxLineWidth) {
                    maxLineWidth = w;
                }
            }
        }
        // Insets: padding left(4) + right(4) = 8px; border: 2px
        // Arrow button (if menu/split): 20px
        // Generous buffer for font kerning, subpixel rendering and Windows display scaling: 26px
        double extra = hasArrow ? 52.0 : 36.0;
        double calculated = Math.ceil(maxLineWidth) + extra;
        double minFloor = hasArrow ? 68.0 : 58.0;
        return Math.max(minFloor, calculated);
    }

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
        btn.setGraphicTextGap(2);
        btn.setPadding(new javafx.geometry.Insets(3, 4, 3, 4));
        double reqW = computeRequiredLargeButtonWidth(text, false);
        btn.setMinWidth(reqW);
        btn.setPrefWidth(reqW);
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
        btn.setGraphicTextGap(2);
        btn.setPadding(new javafx.geometry.Insets(3, 4, 3, 4));
        double reqW = computeRequiredLargeButtonWidth(text, false);
        btn.setMinWidth(reqW);
        btn.setPrefWidth(reqW);
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
        if (large) {
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setTextAlignment(TextAlignment.CENTER);
            btn.setAlignment(Pos.CENTER);
            btn.setGraphicTextGap(2);
            btn.setPadding(new javafx.geometry.Insets(3, 4, 3, 4));
            double reqW = computeRequiredLargeButtonWidth(text, true);
            btn.setMinWidth(reqW);
            btn.setPrefWidth(reqW);
            btn.getStyleClass().add("ribbon-large-menu-btn");
        } else {
            btn.setContentDisplay(ContentDisplay.LEFT);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setMinWidth(Region.USE_PREF_SIZE);
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
        if (large) {
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setTextAlignment(TextAlignment.CENTER);
            btn.setAlignment(Pos.CENTER);
            btn.setGraphicTextGap(2);
            btn.setPadding(new javafx.geometry.Insets(3, 4, 3, 4));
            double reqW = computeRequiredLargeButtonWidth(text, true);
            btn.setMinWidth(reqW);
            btn.setPrefWidth(reqW);
            btn.getStyleClass().add("ribbon-large-split-btn");
        } else {
            btn.setContentDisplay(ContentDisplay.LEFT);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setMinWidth(Region.USE_PREF_SIZE);
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
            btn.setGraphic(AppIcons.of(ikon, large ? 22 : 16));
        }
        btn.setWrapText(false);
        if (large) {
            double reqW = computeRequiredLargeButtonWidth(text, false);
            btn.setMinWidth(reqW);
            btn.setPrefWidth(reqW);
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setTextAlignment(TextAlignment.CENTER);
            btn.setAlignment(Pos.CENTER);
            btn.getStyleClass().add("ribbon-large-btn");
        } else {
            btn.setMinWidth(Region.USE_PREF_SIZE);
            btn.setContentDisplay(ContentDisplay.LEFT);
            btn.setTextAlignment(TextAlignment.LEFT);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.getStyleClass().add("ribbon-btn");
        }
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
