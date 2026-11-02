package org.originsd.oqteris.writer.model;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

/**
 * Embedded image element in a document flow.
 * Supports Office-like wrap modes and absolute positioning
 * (inline, square, top-and-bottom, in-front-of-text, behind-text).
 */
public class DocumentImage implements DocumentElement {

    /**
     * How text flows relative to the image (Word "Wrap Text" options).
     */
    public enum WrapMode {
        INLINE,
        SQUARE,
        TIGHT,
        TOP_AND_BOTTOM,
        BEHIND_TEXT,
        IN_FRONT_OF_TEXT
    }

    public enum HRelative {
        PAGE, MARGIN, COLUMN, CHARACTER, LEFT_MARGIN, RIGHT_MARGIN, INSIDE_MARGIN, OUTSIDE_MARGIN
    }

    public enum VRelative {
        PAGE, MARGIN, PARAGRAPH, LINE, TOP_MARGIN, BOTTOM_MARGIN, INSIDE_MARGIN, OUTSIDE_MARGIN
    }

    public enum HAlign {
        LEFT, CENTER, RIGHT, ABSOLUTE
    }

    private Image image;
    private byte[] rawBytes;
    private double width = 200.0;
    private double height = 150.0;
    private String alignment = "CENTER";
    private String caption = "";

    private int rotationDegrees = 0;

    private double cropLeft = 0;
    private double cropTop = 0;
    private double cropRight = 0;
    private double cropBottom = 0;

    private double brightness = 0;

    private WrapMode wrapMode = WrapMode.INLINE;
    private boolean floating = false;
    private HRelative hRelative = HRelative.PAGE;
    private VRelative vRelative = VRelative.PARAGRAPH;
    private HAlign hAlign = HAlign.LEFT;
    private double hOffsetPt = 0;
    private double vOffsetPt = 0;
    private boolean behindText = false;
    private double distLeft = 9, distRight = 9, distTop = 0, distBottom = 0;

    public DocumentImage() {
    }

    public DocumentImage(Image image, double width, double height) {
        this.image = image;
        this.width = width;
        this.height = height;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.IMAGE;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(10.0, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(10.0, height);
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment != null ? alignment : "CENTER";
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption != null ? caption : "";
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public void setRotationDegrees(int degrees) {
        int d = degrees % 360;
        if (d < 0) d += 360;
        this.rotationDegrees = ((d + 45) / 90) * 90 % 360;
    }

    public void rotateClockwise90() {
        setRotationDegrees(rotationDegrees + 90);
    }

    public void rotateCounterClockwise90() {
        setRotationDegrees(rotationDegrees - 90);
    }

    public double getCropLeft() {
        return cropLeft;
    }

    public double getCropTop() {
        return cropTop;
    }

    public double getCropRight() {
        return cropRight;
    }

    public double getCropBottom() {
        return cropBottom;
    }

    public void setCrop(double left, double top, double right, double bottom) {
        this.cropLeft = clamp01(left);
        this.cropTop = clamp01(top);
        this.cropRight = clamp01(right);
        this.cropBottom = clamp01(bottom);
    }

    public void clearCrop() {
        cropLeft = cropTop = cropRight = cropBottom = 0;
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = Math.max(-1.0, Math.min(1.0, brightness));
    }

    public WrapMode getWrapMode() {
        return wrapMode;
    }

    public void setWrapMode(WrapMode wrapMode) {
        this.wrapMode = wrapMode != null ? wrapMode : WrapMode.INLINE;
        this.floating = this.wrapMode != WrapMode.INLINE;
    }

    public boolean isFloating() {
        return floating || wrapMode == WrapMode.IN_FRONT_OF_TEXT
                || wrapMode == WrapMode.BEHIND_TEXT
                || wrapMode == WrapMode.SQUARE
                || wrapMode == WrapMode.TIGHT
                || wrapMode == WrapMode.TOP_AND_BOTTOM;
    }

    public void setFloating(boolean floating) {
        this.floating = floating;
    }

    public HRelative getHRelative() {
        return hRelative;
    }

    public void setHRelative(HRelative hRelative) {
        this.hRelative = hRelative != null ? hRelative : HRelative.PAGE;
    }

    public VRelative getVRelative() {
        return vRelative;
    }

    public void setVRelative(VRelative vRelative) {
        this.vRelative = vRelative != null ? vRelative : VRelative.PARAGRAPH;
    }

    public HAlign getHAlign() {
        return hAlign;
    }

    public void setHAlign(HAlign hAlign) {
        this.hAlign = hAlign != null ? hAlign : HAlign.LEFT;
    }

    public double getHOffsetPt() {
        return hOffsetPt;
    }

    public void setHOffsetPt(double hOffsetPt) {
        this.hOffsetPt = hOffsetPt;
    }

    public double getVOffsetPt() {
        return vOffsetPt;
    }

    public void setVOffsetPt(double vOffsetPt) {
        this.vOffsetPt = vOffsetPt;
    }

    public boolean isBehindText() {
        return behindText;
    }

    public void setBehindText(boolean behindText) {
        this.behindText = behindText;
    }

    public double getDistLeft() {
        return distLeft;
    }

    public double getDistRight() {
        return distRight;
    }

    public double getDistTop() {
        return distTop;
    }

    public double getDistBottom() {
        return distBottom;
    }

    public void setDist(double left, double right, double top, double bottom) {
        this.distLeft = left;
        this.distRight = right;
        this.distTop = top;
        this.distBottom = bottom;
    }

    /**
     * Returns a display Image with crop + brightness applied
     * (rotation is handled at render time).
     */
    public Image getDisplayImage() {
        if (image == null || image.isError()) return image;
        boolean needsCrop = cropLeft > 0 || cropTop > 0 || cropRight > 0 || cropBottom > 0;
        boolean needsBright = Math.abs(brightness) > 0.01;
        if (!needsCrop && !needsBright) return image;

        int origW = (int) image.getWidth();
        int origH = (int) image.getHeight();
        if (origW <= 0 || origH <= 0) return image;

        int sx = (int) Math.round(cropLeft * origW);
        int sy = (int) Math.round(cropTop * origH);
        int sw = (int) Math.round((1.0 - cropLeft - cropRight) * origW);
        int sh = (int) Math.round((1.0 - cropTop - cropBottom) * origH);
        sx = Math.max(0, Math.min(sx, origW - 1));
        sy = Math.max(0, Math.min(sy, origH - 1));
        sw = Math.max(1, Math.min(sw, origW - sx));
        sh = Math.max(1, Math.min(sh, origH - sy));

        WritableImage out = new WritableImage(sw, sh);
        PixelReader reader = image.getPixelReader();
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                Color c = reader.getColor(sx + x, sy + y);
                if (needsBright) {
                    double f = 1.0 + brightness;
                    c = new Color(
                            clamp01(c.getRed() * f),
                            clamp01(c.getGreen() * f),
                            clamp01(c.getBlue() * f),
                            c.getOpacity());
                }
                writer.setColor(x, y, c);
            }
        }
        return out;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}