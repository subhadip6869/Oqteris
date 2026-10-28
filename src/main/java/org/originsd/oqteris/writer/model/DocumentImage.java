package org.originsd.oqteris.writer.model;

import javafx.scene.image.Image;

/**
 * Embedded image element in a document flow.
 */
public class DocumentImage implements DocumentElement {
    private Image image;
    private byte[] rawBytes;
    private double width = 200.0;
    private double height = 150.0;
    private String alignment = "CENTER"; // LEFT, CENTER, RIGHT
    private String caption = "";

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
        this.alignment = alignment;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption != null ? caption : "";
    }
}
