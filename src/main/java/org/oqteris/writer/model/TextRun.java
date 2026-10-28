package org.oqteris.writer.model;

import javafx.scene.paint.Color;

/**
 * A contiguous sequence of characters sharing identical typographical attributes.
 */
public class TextRun {
    private String text = "";
    private String fontFamily = "Segoe UI";
    private double fontSize = 11.0;
    private boolean bold = false;
    private boolean italic = false;
    private boolean underline = false;
    private boolean strikethrough = false;
    private Color textColor = Color.BLACK;
    private Color highlightColor = null; // None
    private String hyperlinkUrl = null;

    public TextRun() {
    }

    public TextRun(String text) {
        this.text = text != null ? text : "";
    }

    public TextRun(String text, String fontFamily, double fontSize, boolean bold, boolean italic,
                   boolean underline, boolean strikethrough, Color textColor, Color highlightColor) {
        this.text = text != null ? text : "";
        this.fontFamily = fontFamily != null ? fontFamily : "Segoe UI";
        this.fontSize = fontSize;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.textColor = textColor != null ? textColor : Color.BLACK;
        this.highlightColor = highlightColor;
    }

    public TextRun copy() {
        TextRun r = new TextRun(this.text);
        r.fontFamily = this.fontFamily;
        r.fontSize = this.fontSize;
        r.bold = this.bold;
        r.italic = this.italic;
        r.underline = this.underline;
        r.strikethrough = this.strikethrough;
        r.textColor = this.textColor;
        r.highlightColor = this.highlightColor;
        r.hyperlinkUrl = this.hyperlinkUrl;
        return r;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily != null ? fontFamily : "Segoe UI";
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = Math.max(4.0, fontSize);
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    public boolean isStrikethrough() {
        return strikethrough;
    }

    public void setStrikethrough(boolean strikethrough) {
        this.strikethrough = strikethrough;
    }

    public Color getTextColor() {
        return textColor;
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor != null ? textColor : Color.BLACK;
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(Color highlightColor) {
        this.highlightColor = highlightColor;
    }

    public String getHyperlinkUrl() {
        return hyperlinkUrl;
    }

    public void setHyperlinkUrl(String hyperlinkUrl) {
        this.hyperlinkUrl = hyperlinkUrl;
    }
}
