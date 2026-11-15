package org.originsd.oqteris.pdf.render;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles rendering PDF pages to JavaFX Images with caching and rotation support.
 * Optimized with memory bounds to avoid holding uncompressed raster buffers indefinitely.
 */
public class PdfPageRenderer {
    /** Screen DPI used for the 100 % display size of a page. */
    public static final double SCREEN_DPI = 96.0;
    /** Extra pixels rendered per screen pixel so text and vectors stay sharp. */
    public static final double SUPERSAMPLE = 1.25;

    // Strict bounded LRU caches to keep memory footprint flat during scrolling
    private static final int MAX_PAGE_CACHE = 4;
    private static final int MAX_THUMB_CACHE = 16;

    private final PDDocument pdDocument;
    private final PDFRenderer pdfRenderer;
    private Field rendererPageImageField;

    // Strict LRU cache to prevent heap bloat on documents with many pages
    private final Map<String, Image> pageCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Image>(MAX_PAGE_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > MAX_PAGE_CACHE;
                }
            }
    );

    private final Map<Integer, Image> thumbCache = Collections.synchronizedMap(
            new LinkedHashMap<Integer, Image>(MAX_THUMB_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Image> eldest) {
                    return size() > MAX_THUMB_CACHE;
                }
            }
    );

    public PdfPageRenderer(PDDocument pdDocument) {
        this.pdDocument = pdDocument;
        this.pdfRenderer = new PDFRenderer(pdDocument);
        try {
            rendererPageImageField = PDFRenderer.class.getDeclaredField("pageImage");
            rendererPageImageField.setAccessible(true);
        } catch (Throwable ignored) {
            rendererPageImageField = null;
        }
    }

    /**
     * DPI used to rasterize a page at the given zoom. The displayed size of the resulting image is
     * {@code image.getWidth() * SCREEN_DPI * zoom / dpiFor(zoom)} (see PdfPageView).
     */
    public static float dpiFor(double zoom) {
        return (float) Math.max(72.0, Math.min(240.0, SCREEN_DPI * SUPERSAMPLE * Math.min(1.5, zoom)));
    }

    public Image renderPage(int pageIndex, double zoom) {
        return renderPage(pageIndex, zoom, 0);
    }

    /**
     * Renders a page with high-DPI supersampling and user rotation (0, 90, 180, 270).
     */
    public Image renderPage(int pageIndex, double zoom, int additionalRotation) {
        if (pageIndex < 0 || pageIndex >= pdDocument.getNumberOfPages()) {
            return null;
        }

        float dpi = dpiFor(zoom);
        int rotation = (additionalRotation % 360 + 360) % 360;
        String cacheKey = pageIndex + ":" + ((int) dpi) + ":" + rotation;

        Image cached = pageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        synchronized (pdfRenderer) {
            // Re-check after acquiring lock
            cached = pageCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            BufferedImage bImg = null;
            try {
                bImg = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
                if (rotation != 0) {
                    BufferedImage rotated = rotateImage(bImg, rotation);
                    bImg.flush();
                    bImg = rotated;
                }
                Image fxImg = SwingFXUtils.toFXImage(bImg, null);
                bImg.flush();
                bImg = null;

                if (fxImg != null) {
                    pageCache.put(cacheKey, fxImg);
                }
                clearRendererPageImage();
                return fxImg;
            } catch (Exception e) {
                if (bImg != null) {
                    bImg.flush();
                }
                System.err.println("[PdfPageRenderer] Error rendering page " + pageIndex + ": " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Renders a small thumbnail for sidebar navigation.
     */
    public Image renderThumbnail(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pdDocument.getNumberOfPages()) {
            return null;
        }
        Image cached = thumbCache.get(pageIndex);
        if (cached != null) return cached;

        synchronized (pdfRenderer) {
            cached = thumbCache.get(pageIndex);
            if (cached != null) return cached;
            BufferedImage bImg = null;
            try {
                bImg = pdfRenderer.renderImageWithDPI(pageIndex, 36.0f);
                Image fxImg = SwingFXUtils.toFXImage(bImg, null);
                bImg.flush();
                bImg = null;

                if (fxImg != null) {
                    thumbCache.put(pageIndex, fxImg);
                }
                clearRendererPageImage();
                return fxImg;
            } catch (Exception e) {
                if (bImg != null) {
                    bImg.flush();
                }
                return null;
            }
        }
    }

    public void clearCache() {
        pageCache.clear();
        thumbCache.clear();
        clearRendererPageImage();
    }

    public void evictPage(int pageIndex) {
        String prefix = pageIndex + ":";
        pageCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void clearRendererPageImage() {
        if (rendererPageImageField != null) {
            try {
                Object img = rendererPageImageField.get(pdfRenderer);
                if (img instanceof BufferedImage bi) {
                    bi.flush();
                }
                rendererPageImageField.set(pdfRenderer, null);
            } catch (Throwable ignored) {
            }
        }
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
