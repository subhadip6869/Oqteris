package org.oqteris.presentation.model;

import javafx.scene.image.Image;

/**
 * Embedded image shape on a slide.
 */
public class ImageShape extends SlideShape {
    private Image image;
    private byte[] rawBytes;

    public ImageShape(Image image, double x, double y, double width, double height) {
        super(ShapeType.IMAGE, x, y, width, height);
        this.image = image;
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

    @Override
    public SlideShape copy() {
        ImageShape s = new ImageShape(image, getX(), getY(), getWidth(), getHeight());
        s.rawBytes = this.rawBytes;
        s.setRotation(getRotation());
        return s;
    }
}
