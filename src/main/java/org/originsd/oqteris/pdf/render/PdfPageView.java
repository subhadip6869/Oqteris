package org.originsd.oqteris.pdf.render;

import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.crypto.signature.PdfSignatureVerifier;
import org.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import org.originsd.oqteris.pdf.model.PdfAnnotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Visual node representing an individual PDF page on the document surface.
 * Shows a loading placeholder until the page image is rendered asynchronously.
 */
public class PdfPageView extends StackPane {
    private final int pageIndex;
    private final ImageView imageView = new ImageView();
    private final Canvas overlayCanvas = new Canvas();
    private final Label loadingLabel;
    private final List<PdfAnnotation> annotations = new ArrayList<>();
    private final List<double[]> searchHighlights = new ArrayList<>(); // [x, y, w, h] in relative ratios

    // Digital signature overlay state
    private final List<PdfSignatureInfo> pageSignatures = new ArrayList<>();
    private int viewRotation = 0;
    private Consumer<PdfSignatureInfo> onSignatureClicked;
    // Signatures whose own appearance already shows the validity mark (layered appearance); no overlay needed
    private Set<PdfSignatureInfo> layeredSignatures = Collections.emptySet();

    public PdfPageView(int pageIndex) {
        this.pageIndex = pageIndex;
        this.loadingLabel = new Label("Loading page " + (pageIndex + 1) + "...");
        setAlignment(Pos.CENTER);
        getStyleClass().add("canvas-page");

        // Loading placeholder appearance
        setPrefWidth(816);   // Approx A4 at 96dpi
        setPrefHeight(1056); // Approx A4 at 96dpi
        setStyle("-fx-background-color: #f0f0f0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 10, 0, 0, 2);");

        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #888888;");

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        // Allow image to scale with parent width
        imageView.setFitWidth(0); // 0 = natural size initially

        overlayCanvas.setMouseTransparent(true);
        getChildren().addAll(loadingLabel, imageView, overlayCanvas);

        // Clicking a signature field opens its details (the canvas is mouse-transparent, so listen here)
        setOnMouseClicked(e -> {
            if (onSignatureClicked == null) return;
            double pw = overlayCanvas.getWidth();
            double ph = overlayCanvas.getHeight();
            // The canvas is centered in the StackPane; convert to canvas coordinates
            double offX = (getWidth() - pw) / 2.0;
            double offY = (getHeight() - ph) / 2.0;
            double mx = e.getX() - offX;
            double my = e.getY() - offY;
            for (PdfSignatureInfo s : pageSignatures) {
                double[] r = signatureRect(s);
                if (mx >= r[0] * pw && mx <= (r[0] + r[2]) * pw
                        && my >= r[1] * ph && my <= (r[1] + r[3]) * ph) {
                    onSignatureClicked.accept(s);
                    return;
                }
            }
        });
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void updatePageImage(Image image) {
        updatePageImage(image, 1.0);
    }

    public void updatePageImage(Image image, double zoom) {
        if (image == null) return;
        imageView.setImage(image);
        loadingLabel.setVisible(false);
        loadingLabel.setManaged(false);

        // High-DPI supersampling: rendered raster is 144 DPI, downsampled to 96 DPI display
        double scaleRatio = 144.0 / 96.0;
        double displayW = image.getWidth() / scaleRatio;
        double displayH = image.getHeight() / scaleRatio;

        // Set preferred size based on sharp display dimensions
        setPrefSize(displayW, displayH);
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(displayW, displayH);

        // White background now that we have content
        setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 10, 0, 0, 2);");

        overlayCanvas.setWidth(displayW);
        overlayCanvas.setHeight(displayH);
        imageView.setFitWidth(displayW);
        imageView.setFitHeight(displayH);

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

    /**
     * Supplies the verified signatures of the document. Only visible signatures located on this page are drawn.
     *
     * @param userRotation the extra rotation (0/90/180/270) applied by the viewer's Rotate button
     */
    public void setSignatures(List<PdfSignatureInfo> sigs, int userRotation, Set<PdfSignatureInfo> layered) {
        pageSignatures.clear();
        layeredSignatures = layered != null ? layered : Collections.emptySet();
        viewRotation = ((userRotation % 360) + 360) % 360;
        if (sigs != null) {
            for (PdfSignatureInfo s : sigs) {
                if (s.isVisual() && s.getVisualRect() != null && s.getPageIndex() == pageIndex) {
                    pageSignatures.add(s);
                }
            }
        }
        redrawOverlay();
    }

    public void setOnSignatureClicked(Consumer<PdfSignatureInfo> callback) {
        this.onSignatureClicked = callback;
    }

    private double[] signatureRect(PdfSignatureInfo s) {
        double[] v = s.getVisualRect();
        return PdfSignatureVerifier.rotateNormRect(v[0], v[1], v[2], v[3], viewRotation);
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
                    gc.setStroke(a.getColor());
                    gc.setLineWidth(2.0);
                    gc.strokeRect(ax, ay, aw, ah);
                }
            }
        }

        // 3. Digital signature badges (green tick / yellow ? / red X).
        // Skipped for layered appearances: there the signature's own n1/n3/n4 layers already show the state.
        for (PdfSignatureInfo s : pageSignatures) {
            if (layeredSignatures.contains(s)) continue;
            double[] r = signatureRect(s);
            double sx = r[0] * pageW;
            double sy = r[1] * pageH;
            double sw = r[2] * pageW;
            double sh = r[3] * pageH;

            SignatureVerificationStatus st = s.getStatus();
            boolean trusted = st == SignatureVerificationStatus.VALID_AND_TRUSTED;
            boolean untrusted = st == SignatureVerificationStatus.VALID_BUT_UNTRUSTED;
            Color c = Color.web(st.getColorHex());

            // Field outline
            gc.setStroke(c);
            gc.setLineWidth(1.5);
            gc.strokeRect(sx, sy, sw, sh);

            // Round badge in the top-left corner of the field
            double d = Math.max(14, Math.min(26, Math.min(sw, sh) * 0.45));
            double cx = sx + d / 2 + 2;
            double cy = sy + d / 2 + 2;
            gc.setFill(c);
            gc.fillOval(cx - d / 2, cy - d / 2, d, d);

            Color glyph = untrusted ? Color.web("#1e293b") : Color.WHITE;
            gc.setStroke(glyph);
            gc.setFill(glyph);
            gc.setLineWidth(Math.max(2, d / 8));
            gc.setLineCap(StrokeLineCap.ROUND);
            double u = d / 4;
            if (trusted) {                                                // green tick
                gc.strokeLine(cx - u, cy, cx - u / 3, cy + u * 0.8);
                gc.strokeLine(cx - u / 3, cy + u * 0.8, cx + u, cy - u * 0.8);
            } else if (untrusted) {                                       // yellow ?
                gc.setFont(Font.font("System", FontWeight.BOLD, d * 0.75));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText("?", cx, cy + 1);
            } else {                                                      // red X
                gc.strokeLine(cx - u, cy - u, cx + u, cy + u);
                gc.strokeLine(cx - u, cy + u, cx + u, cy - u);
            }
        }
    }
}