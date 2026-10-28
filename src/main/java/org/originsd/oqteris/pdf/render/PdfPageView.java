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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.pdf.model.PdfAnnotation;

import java.util.ArrayList;
import java.util.List;

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
    private final List<PdfSignatureInfo> signatures = new ArrayList<>();
    private final List<double[]> searchHighlights = new ArrayList<>(); // [x, y, w, h] in relative ratios

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

    public void setSignatures(List<PdfSignatureInfo> sigs) {
        signatures.clear();
        if (sigs != null) {
            for (PdfSignatureInfo s : sigs) {
                if (s.isVisual() && s.getPageIndex() == pageIndex) {
                    signatures.add(s);
                }
            }
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
                    gc.setStroke(a.getColor());
                    gc.setLineWidth(2.0);
                    gc.strokeRect(ax, ay, aw, ah);
                }
            }
        }

        // 3. Draw Digital Signatures (Authentic Foxit/Adobe signature UI as in official verification)
        for (PdfSignatureInfo sig : signatures) {
            double[] r = sig.getVisualRect();
            if (r == null || r.length < 4) continue;
            double sx = r[0] * pageW;
            double sy = r[1] * pageH;
            double sw = r[2] * pageW;
            double sh = r[3] * pageH;

            // Pure white background to cleanly cover the unverified raster from PDFBox
            gc.setFill(Color.WHITE);
            gc.fillRect(sx, sy, sw, sh);

            boolean isTrusted = sig.getStatus() == org.originsd.oqteris.crypto.signature.SignatureVerificationStatus.VALID_AND_TRUSTED;
            boolean isUntrusted = sig.getStatus() == org.originsd.oqteris.crypto.signature.SignatureVerificationStatus.VALID_BUT_UNTRUSTED;

            // Heading: 'Signature valid' when trusted, 'Signature Not Verified' when untrusted
            String heading = isTrusted ? "Signature valid" : (isUntrusted ? "Signature Not Verified" : "Signature invalid");
            double headingSize = Math.max(11.0, Math.min(16.0, sh * 0.22));

            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, headingSize));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);
            gc.fillText(heading, sx + 2, sy + 2);

            // Detail text lines (preserving original layout: Digitally Signed., Name, Date, Reason, Location)
            double textSize = Math.max(8.0, Math.min(10.5, sh * 0.14));
            double lineSpacing = textSize * 1.30;
            double textY = sy + headingSize + 6;
            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, textSize));

            // Line 1: Digitally Signed.
            gc.fillText("Digitally Signed.", sx + 2, textY);
            textY += lineSpacing;

            // Line 2: Name
            String signerName = sig.getSignerName() != null ? sig.getSignerName() : "Unknown";
            gc.fillText("Name: " + signerName, sx + 2, textY);
            textY += lineSpacing;

            // Line 3: Date
            if (sig.getSigningTime() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", java.util.Locale.ENGLISH);
                gc.fillText("Date: " + sdf.format(sig.getSigningTime()), sx + 2, textY);
                textY += lineSpacing;
            }

            // Line 4: Reason (if present)
            if (sig.getReason() != null && !sig.getReason().isBlank()) {
                gc.fillText("Reason: " + sig.getReason(), sx + 2, textY);
                textY += lineSpacing;
            }

            // Line 5: Location (if present)
            if (sig.getLocation() != null && !sig.getLocation().isBlank()) {
                gc.fillText("Location: " + sig.getLocation(), sx + 2, textY);
            }

            // Status icon: Green checkmark with black border on the right (like Foxit/Adobe)
            double iconW = Math.max(28.0, Math.min(42.0, sw * 0.32));
            double iconH = Math.max(34.0, Math.min(50.0, sh * 0.72));
            double iconX = sx + Math.max(sw * 0.40, sw * 0.58);
            double iconY = sy + Math.max(2.0, (sh - iconH) / 2.0 - 2);

            if (isTrusted) {
                drawAdobeFoxitCheckmark(gc, iconX, iconY, iconW, iconH);
            } else if (isUntrusted) {
                drawAdobeFoxitQuestionMark(gc, iconX, iconY, iconW, iconH);
            } else {
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, iconH * 0.95));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.setFill(Color.rgb(220, 38, 38));
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(1.6);
                gc.strokeText("✗", iconX + iconW / 2.0, iconY + iconH / 2.0);
                gc.fillText("✗", iconX + iconW / 2.0, iconY + iconH / 2.0);
            }
        }
    }

    private void drawAdobeFoxitCheckmark(GraphicsContext gc, double x, double y, double w, double h) {
        // Authentic Foxit / Adobe green checkmark with black outline
        double[] px = {
                x + w * 0.05,
                x + w * 0.35,
                x + w * 0.88,
                x + w * 1.00,
                x + w * 0.38,
                x + w * 0.00
        };
        double[] py = {
                y + h * 0.58,
                y + h * 0.88,
                y + h * 0.00,
                y + h * 0.08,
                y + h * 1.00,
                y + h * 0.68
        };

        gc.setFill(Color.rgb(0, 166, 81)); // Authentic Foxit/Adobe signature green #00a651
        gc.fillPolygon(px, py, 6);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.6);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        gc.strokePolygon(px, py, 6);
    }

    private void drawAdobeFoxitQuestionMark(GraphicsContext gc, double x, double y, double w, double h) {
        // Authentic Foxit / Adobe yellow question mark with black outline
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, h * 0.95));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        double cx = x + w / 2.0;
        double cy = y + h / 2.0;

        gc.setFill(Color.rgb(255, 204, 0)); // Foxit yellow
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.6);

        gc.strokeText("?", cx, cy);
        gc.fillText("?", cx, cy);
    }
}
