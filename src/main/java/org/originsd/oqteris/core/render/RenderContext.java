package org.originsd.oqteris.core.render;

import javafx.scene.paint.Color;

/**
 * Encapsulates view rendering state, scaling, and theme information for canvas renderers.
 */
public class RenderContext {
    private double zoom = 1.0;
    private double dpiScale = 1.0;
    private boolean darkTheme = false;
    private Color selectionColor = Color.rgb(0, 120, 215, 0.35);
    private Color selectionBorderColor = Color.rgb(0, 120, 215, 0.9);
    private Color gridColor = Color.rgb(220, 220, 220);
    private Color pageBackgroundColor = Color.WHITE;
    private Color pageShadowColor = Color.rgb(0, 0, 0, 0.25);

    public RenderContext() {
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.2, Math.min(5.0, zoom));
    }

    public double getDpiScale() {
        return dpiScale;
    }

    public void setDpiScale(double dpiScale) {
        this.dpiScale = Math.max(0.5, dpiScale);
    }

    public double getEffectiveScale() {
        return zoom * dpiScale;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        if (darkTheme) {
            this.gridColor = Color.rgb(55, 55, 60);
            this.pageShadowColor = Color.rgb(0, 0, 0, 0.6);
        } else {
            this.gridColor = Color.rgb(220, 220, 220);
            this.pageShadowColor = Color.rgb(0, 0, 0, 0.25);
        }
    }

    public Color getSelectionColor() {
        return selectionColor;
    }

    public void setSelectionColor(Color selectionColor) {
        this.selectionColor = selectionColor;
    }

    public Color getSelectionBorderColor() {
        return selectionBorderColor;
    }

    public void setSelectionBorderColor(Color selectionBorderColor) {
        this.selectionBorderColor = selectionBorderColor;
    }

    public Color getGridColor() {
        return gridColor;
    }

    public Color getPageBackgroundColor() {
        return pageBackgroundColor;
    }

    public void setPageBackgroundColor(Color pageBackgroundColor) {
        this.pageBackgroundColor = pageBackgroundColor;
    }

    public Color getPageShadowColor() {
        return pageShadowColor;
    }
}
