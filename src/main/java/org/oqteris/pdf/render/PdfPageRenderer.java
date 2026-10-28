package org.oqteris.pdf.render;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles rendering PDF pages to JavaFX Images with caching and rotation support.
 */
public class PdfPageRenderer {
    private final PDDocument pdDocument;
    private final PDFRenderer pdfRenderer;
    private final Map<String, Image> pageCache = new ConcurrentHashMap<>();

    public PdfPageRenderer(PDDocument pdDocument) {
        this.pdDocument = pdDocument;
        this.pdfRenderer = new PDFRenderer(pdDocument);
    }

    /**
     * Renders a page with the given zoom factor and user rotation (0, 90, 180, 270).
     */
    public Image renderPage(int pageIndex, double zoom, int additionalRotation) {
        if (pageIndex < 0 || pageIndex >= pdDocument.getNumberOfPages()) {
            return null;
        }

        // DPI scaled by zoom, clamped to avoid memory exhaustion
        float dpi = (float) Math.max(36.0, Math.min(300.0, 96.0 * zoom));
        int rotation = (additionalRotation % 360 + 360) % 360;
        String cacheKey = pageIndex + ":" + ((int) dpi) + ":" + rotation;

        Image cached = pageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            BufferedImage bImg = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
            if (rotation != 0) {
                bImg = rotateImage(bImg, rotation);
            }
            Image fxImg = SwingFXUtils.toFXImage(bImg, null);
            pageCache.put(cacheKey, fxImg);
            return fxImg;
        } catch (Exception e) {
            System.err.println("[PdfPageRenderer] Error rendering page " + pageIndex + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Renders a small thumbnail for sidebar navigation.
     */
    public Image renderThumbnail(int pageIndex) {
        String key = "thumb:" + pageIndex;
        Image cached = pageCache.get(key);
        if (cached != null) return cached;

        try {
            BufferedImage bImg = pdfRenderer.renderImageWithDPI(pageIndex, 36.0f);
            Image fxImg = SwingFXUtils.toFXImage(bImg, null);
            pageCache.put(key, fxImg);
            return fxImg;
        } catch (Exception e) {
            return null;
        }
    }

    public void clearCache() {
        pageCache.clear();
    }

    private static BufferedImage rotateImage(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();

        int newW = (angle == 90 || angle == 270) ? h : w;
        int newH = (angle == 90 || angle == 270) ? w : h;

        BufferedImage rotated = new BufferedImage(newW, newH, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
        AffineTransform at = new AffineTransform();

        at.translate((newW - w) / 2.0, (newH - h) / 2.0);
        at.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);

        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        return op.filter(img, rotated);
    }
}
