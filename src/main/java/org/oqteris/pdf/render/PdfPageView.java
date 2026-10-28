package org.oqteris.pdf.render;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.oqteris.pdf.model.PdfAnnotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual node representing an individual PDF page on the document surface.
 */
public class PdfPageView extends StackPane {
    private final int pageIndex;
    private final ImageView imageView = new ImageView();
    private final Canvas overlayCanvas = new Canvas();
    private final List<PdfAnnotation> annotations = new ArrayList<>();
    private final List<double[]> searchHighlights = new ArrayList<>(); // [x, y, w, h] in relative ratios

    public PdfPageView(int pageIndex) {
        this.pageIndex = pageIndex;
        setAlignment(Pos.CENTER);
        getStyleClass().add("canvas-page");
        setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 10, 0, 0, 2);");

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Overlay canvas matches image size
        getChildren().addAll(imageView, overlayCanvas);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void updatePageImage(Image image) {
        if (image == null) return;
        imageView.setImage(image);
        double w = image.getWidth();
        double h = image.getHeight();

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        overlayCanvas.setWidth(w);
        overlayCanvas.setHeight(h);

        redrawOverlay();
    }

    public void setAnnotations(List<PdfAnnotation> annots) {
        annotations.clear();
        if (annots != null) {
            for (PdfAnnotation a : annots) {
                if (a.getPageIndex() == pageIndex) {
                    annotations.add(a);
                }
            }
        }
        redrawOverlay();
    }

    public void setSearchHighlights(List<double[]> highlights) {
        searchHighlights.clear();
        if (highlights != null) {
            searchHighlights.addAll(highlights);
        }
        redrawOverlay();
    }

    public void redrawOverlay() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        double pageW = overlayCanvas.getWidth();
        double pageH = overlayCanvas.getHeight();

        // 1. Draw search highlights
        gc.setFill(Color.rgb(255, 235, 59, 0.45));
        for (double[] rect : searchHighlights) {
            if (rect.length >= 4) {
                gc.fillRect(rect[0] * pageW, rect[1] * pageH, rect[2] * pageW, rect[3] * pageH);
                gc.setStroke(Color.ORANGE);
                gc.setLineWidth(1.5);
                gc.strokeRect(rect[0] * pageW, rect[1] * pageH, rect[2] * pageW, rect[3] * pageH);
            }
        }

        // 2. Draw user annotations
        for (PdfAnnotation a : annotations) {
            gc.setFill(a.getColor() != null ? a.getColor() : Color.rgb(255, 255, 0, 0.4));
            double ax = a.getX() * pageW;
            double ay = a.getY() * pageH;
            double aw = a.getWidth() * pageW;
            double ah = a.getHeight() * pageH;

            switch (a.getType()) {
                case HIGHLIGHT -> gc.fillRect(ax, ay, aw, ah);
                case UNDERLINE -> {
                    gc.setStroke(a.getColor());
                    gc.setLineWidth(2.0);
                    gc.strokeLine(ax, ay + ah, ax + aw, ay + ah);
                }
                case NOTE -> {
                    gc.fillRect(ax, ay, Math.max(16, aw), Math.max(16, ah));
                    gc.setStroke(Color.DARKGOLDENROD);
                    gc.strokeRect(ax, ay, Math.max(16, aw), Math.max(16, ah));
                }
                case FREEHAND -> {
                    // Stroke rect
                    gc.setStroke(a.getColor());
                    gc.setLineWidth(2.0);
                    gc.strokeRect(ax, ay, aw, ah);
                }
            }
        }
    }
}
