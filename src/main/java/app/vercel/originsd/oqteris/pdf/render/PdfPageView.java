package app.vercel.originsd.oqteris.pdf.render;

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
import app.vercel.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import app.vercel.originsd.oqteris.crypto.signature.PdfSignatureVerifier;
import app.vercel.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import app.vercel.originsd.oqteris.pdf.model.PdfAnnotation;
import app.vercel.originsd.oqteris.pdf.model.PdfPageContent;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Visual node representing an individual PDF page on the document surface.
 * Shows a loading placeholder until the page image is rendered asynchronously.
 *
 * <p><b>Canvas lifecycle:</b> The overlay Canvas is only attached to the scene graph
 * while the page is loaded (has content). It is completely removed from children on
 * {@link #unload()} to prevent JavaFX Prism from attempting {@code NGCanvas.renderForcedContent}
 * on off-screen pages, which causes GPU RTTexture allocation failures and freezes.</p>
 */
public class PdfPageView extends StackPane {
    private final int pageIndex;
    private final ImageView imageView = new ImageView();
    // Canvas is created lazily and removed from scene on unload to prevent Prism RTTexture crashes
    private Canvas overlayCanvas = null;
    private final Label loadingLabel;
    private final List<PdfAnnotation> annotations = new ArrayList<>();
    private final List<double[]> searchHighlights = new ArrayList<>(); // [x, y, w, h] in relative ratios

    // Digital signature overlay state
    private final List<PdfSignatureInfo> pageSignatures = new ArrayList<>();
    private int viewRotation = 0;
    private double renderedZoom = -1.0;
    private int renderedRotation = -1;
    private Consumer<PdfSignatureInfo> onSignatureClicked;
    // Signatures whose own appearance already shows the validity mark (layered appearance); no overlay needed
    private Set<PdfSignatureInfo> layeredSignatures = Collections.emptySet();

    // Selection state
    private double dragStartX;
    private double dragStartY;
    private boolean isDraggingSelection = false;
    private final List<double[]> selectedLineRects = new ArrayList<>();
    private PdfPageContent.PageObject selectedObject = null;
    private double[] selectionRect = null; // Legacy [x, y, w, h]
    private String selectedText = null;
    private boolean handToolActive = false;
    private Consumer<Integer> onSelectionStarted;
    private BiConsumer<Integer, double[]> onSelectionChanged;
    private Consumer<PdfPageContent.PageObject> onObjectSelected;
    private Supplier<PDDocument> documentSupplier;
    private PdfPageContent pageContent;

    public PdfPageView(int pageIndex) {
        this.pageIndex = pageIndex;
        this.loadingLabel = new Label("Loading page " + (pageIndex + 1) + "...");
        setAlignment(Pos.CENTER);
        getStyleClass().add("canvas-page");

        // Loading placeholder - no drop shadow or effects to avoid Prism off-screen render buffers
        setPrefWidth(816);   // Approx A4 at 96dpi
        setPrefHeight(1056); // Approx A4 at 96dpi
        setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px;");

        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #888888;");
        setCursor(javafx.scene.Cursor.TEXT);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(0); // 0 = natural size initially

        // Only ImageView and label are permanent children; Canvas is attached lazily
        getChildren().addAll(loadingLabel, imageView);

        // Hovering over page: dynamic cursor (HAND over clickable objects/signatures, TEXT over text)
        setOnMouseMoved(e -> {
            if (handToolActive) {
                setCursor(javafx.scene.Cursor.OPEN_HAND);
                return;
            }
            double pw = overlayCanvas != null ? overlayCanvas.getWidth() : getWidth();
            double ph = overlayCanvas != null ? overlayCanvas.getHeight() : getHeight();
            if (pw <= 0 || ph <= 0) {
                setCursor(javafx.scene.Cursor.TEXT);
                return;
            }
            double offX = (getWidth() - pw) / 2.0;
            double offY = (getHeight() - ph) / 2.0;
            double normX = (e.getX() - offX) / pw;
            double normY = (e.getY() - offY) / ph;

            ensurePageContent();

            // 1. Signature fields always take precedence and show HAND cursor
            if (hitTestSignature(normX, normY) != null) {
                setCursor(javafx.scene.Cursor.HAND);
                return;
            }

            // 2. Text lines show TEXT cursor
            if (pageContent != null) {
                PdfPageContent.TextLine line = pageContent.hitTestLine(normX, normY);
                if (line != null) {
                    setCursor(javafx.scene.Cursor.TEXT);
                    return;
                }
                PdfPageContent.PageObject obj = pageContent.hitTestObject(normX, normY);
                if (obj != null) {
                    setCursor(javafx.scene.Cursor.HAND);
                    return;
                }
            }
            setCursor(javafx.scene.Cursor.TEXT);
        });

        setOnMouseExited(e -> {
            if (!handToolActive && !isDraggingSelection) {
                setCursor(javafx.scene.Cursor.TEXT);
            }
        });

        // Mouse pressed: records origin and prepares for selection or object click
        setOnMousePressed(e -> {
            if (handToolActive) return;
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;

            double pw = overlayCanvas != null ? overlayCanvas.getWidth() : getWidth();
            double ph = overlayCanvas != null ? overlayCanvas.getHeight() : getHeight();
            double offX = (getWidth() - pw) / 2.0;
            double offY = (getHeight() - ph) / 2.0;

            dragStartX = e.getX() - offX;
            dragStartY = e.getY() - offY;
            isDraggingSelection = false;

            ensurePageContent();

            if (onSelectionStarted != null) {
                onSelectionStarted.accept(pageIndex);
            }

            e.consume();
        });

        // Mouse dragged: line-by-line text selection highlight in real time
        setOnMouseDragged(e -> {
            if (handToolActive) return;
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;

            double pw = overlayCanvas != null ? overlayCanvas.getWidth() : getWidth();
            double ph = overlayCanvas != null ? overlayCanvas.getHeight() : getHeight();
            if (pw <= 0 || ph <= 0) return;

            double offX = (getWidth() - pw) / 2.0;
            double offY = (getHeight() - ph) / 2.0;

            double curX = e.getX() - offX;
            double curY = e.getY() - offY;

            double dist = Math.hypot(curX - dragStartX, curY - dragStartY);
            if (dist > 3.0) {
                isDraggingSelection = true;
                selectedObject = null; // Dragging text clears object selection

                ensurePageContent();
                if (pageContent != null) {
                    double normStartX = Math.max(0.0, Math.min(1.0, dragStartX / pw));
                    double normStartY = Math.max(0.0, Math.min(1.0, dragStartY / ph));
                    double normCurX = Math.max(0.0, Math.min(1.0, curX / pw));
                    double normCurY = Math.max(0.0, Math.min(1.0, curY / ph));

                    PdfPageContent.SelectionResult res = pageContent.computeSelection(normStartX, normStartY, normCurX, normCurY);
                    selectedLineRects.clear();
                    selectedLineRects.addAll(res.lineRects);
                    selectedText = res.text;
                    selectionRect = !selectedLineRects.isEmpty() ? selectedLineRects.get(0) : null;
                } else {
                    double minX = Math.max(0, Math.min(dragStartX, curX));
                    double maxX = Math.min(pw, Math.max(dragStartX, curX));
                    double minY = Math.max(0, Math.min(dragStartY, curY));
                    double maxY = Math.min(ph, Math.max(dragStartY, curY));
                    selectedLineRects.clear();
                    selectedLineRects.add(new double[]{ minX / pw, minY / ph, (maxX - minX) / pw, (maxY - minY) / ph });
                    selectionRect = selectedLineRects.get(0);
                }
                redrawOverlay();
            }

            setCursor(javafx.scene.Cursor.TEXT);
            e.consume();
        });

        // Mouse released: finalizes line-by-line selection or handles object selection on click
        setOnMouseReleased(e -> {
            if (handToolActive) return;
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;

            double pw = overlayCanvas != null ? overlayCanvas.getWidth() : getWidth();
            double ph = overlayCanvas != null ? overlayCanvas.getHeight() : getHeight();
            if (pw <= 0 || ph <= 0) return;

            double offX = (getWidth() - pw) / 2.0;
            double offY = (getHeight() - ph) / 2.0;

            if (isDraggingSelection) {
                isDraggingSelection = false;
                if (!selectedLineRects.isEmpty()) {
                    selectionRect = selectedLineRects.get(0);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.accept(pageIndex, selectionRect);
                    }
                }
            } else {
                // Click (drag distance <= 3px): select object on click or word on double click
                double clickX = e.getX() - offX;
                double clickY = e.getY() - offY;
                double normX = clickX / pw;
                double normY = clickY / ph;

                ensurePageContent();

                // 1. Digital Signatures: Click opens signature properties dialog, NEVER select signature as object!
                PdfSignatureInfo hitSig = hitTestSignature(normX, normY);
                if (hitSig != null) {
                    selectedObject = null;
                    clearSelection();
                    if (onSignatureClicked != null) {
                        onSignatureClicked.accept(hitSig);
                    }
                    e.consume();
                    return;
                }

                // 2. Text lines are TEXT, NEVER objects! If clicked over text:
                PdfPageContent.TextLine hitLine = pageContent != null ? pageContent.hitTestLine(normX, normY) : null;
                if (hitLine != null) {
                    selectedObject = null;
                    if (e.getClickCount() == 2) {
                        // Double click: select word as text highlight
                        PdfPageContent.WordBox wb = pageContent.hitTestWord(normX, normY);
                        if (wb != null) {
                            selectedLineRects.clear();
                            selectedLineRects.add(new double[]{ wb.normX, wb.normY, wb.normW, wb.normH });
                            selectedText = wb.word;
                            selectionRect = selectedLineRects.get(0);
                            redrawOverlay();
                            if (onSelectionChanged != null) {
                                onSelectionChanged.accept(pageIndex, selectionRect);
                            }
                        } else {
                            clearSelection();
                        }
                    } else if (e.getClickCount() == 3) {
                        // Triple click: select line as text highlight
                        selectedLineRects.clear();
                        selectedLineRects.add(new double[]{ hitLine.normX, hitLine.normY, hitLine.normW, hitLine.normH });
                        selectedText = hitLine.text;
                        selectionRect = selectedLineRects.get(0);
                        redrawOverlay();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.accept(pageIndex, selectionRect);
                        }
                    } else {
                        // Single click on text: clear any object or text selection
                        clearSelection();
                    }
                    e.consume();
                    return;
                }

                // 3. If NOT signature and NOT text, check if an actual selectable object was clicked (Image, Shape)
                PdfPageContent.PageObject hitObj = pageContent != null ? pageContent.hitTestObject(normX, normY) : null;
                if (hitObj != null) {
                    if (hitObj.type == PdfPageContent.ObjectType.SIGNATURE) {
                        // Safety fallback: if hitObj is a signature, do NOT select it
                        selectedObject = null;
                        clearSelection();
                        if (onSignatureClicked != null && hitObj.payload instanceof PdfSignatureInfo sig) {
                            onSignatureClicked.accept(sig);
                        }
                    } else {
                        selectedObject = hitObj;
                        selectedLineRects.clear();
                        selectedText = hitObj.title;
                        selectionRect = hitObj.getBounds();
                        redrawOverlay();

                        if (onObjectSelected != null) {
                            onObjectSelected.accept(hitObj);
                        }
                    }
                } else {
                    // Clicked empty page space: clear selection
                    clearSelection();
                }
            }

            e.consume();
        });
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public boolean isRenderedAt(double zoom, int rotation) {
        return imageView.getImage() != null && Math.abs(renderedZoom - zoom) < 0.001 && renderedRotation == rotation;
    }

    public void markUnrendered() {
        this.renderedZoom = -1.0;
        this.renderedRotation = -1;
    }

    /**
     * Unloads the rendered raster image to reclaim heap and GPU memory when scrolled far from viewport.
     * Removes the Canvas from the scene graph entirely to prevent Prism from allocating RTTextures
     * for off-screen Canvas nodes via renderForcedContent, which causes GPU NPE crashes and UI freezes.
     * Preferred dimensions are preserved so scroll position stays stable.
     */
    public void unload() {
        if (imageView.getImage() != null || overlayCanvas != null) {
            imageView.setVisible(false);
            imageView.setImage(null);
            loadingLabel.setVisible(true);
            loadingLabel.setManaged(true);
            markUnrendered();

            // Detach Canvas completely from scene graph — Prism will no longer touch it
            if (overlayCanvas != null) {
                getChildren().remove(overlayCanvas);
                overlayCanvas = null;
            }

            searchHighlights.clear();
            pageSignatures.clear();
            pageContent = null;
            selectedObject = null;
            selectedLineRects.clear();
            selectionRect = null;
            selectedText = null;

            setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px;");
        }
    }

    public void updatePageImage(Image image) {
        updatePageImage(image, 1.0, 0);
    }

    public void updatePageImage(Image image, double zoom) {
        updatePageImage(image, zoom, viewRotation);
    }

    public void updatePageImage(Image image, double zoom, int rotation) {
        if (image == null) return;
        this.renderedZoom = zoom;
        this.renderedRotation = rotation;
        this.viewRotation = rotation;
        imageView.setImage(image);
        imageView.setVisible(true);
        loadingLabel.setVisible(false);
        loadingLabel.setManaged(false);

        // The raster was rendered at dpiFor(zoom); the page is shown at 96 DPI * zoom, so its displayed
        // size scales with the zoom level (this also scales the signature field, which is part of the page).
        double dpi = PdfPageRenderer.dpiFor(zoom);
        double displayW = image.getWidth() * PdfPageRenderer.SCREEN_DPI * zoom / dpi;
        double displayH = image.getHeight() * PdfPageRenderer.SCREEN_DPI * zoom / dpi;

        // Set preferred size based on sharp display dimensions
        setPrefSize(displayW, displayH);
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(displayW, displayH);

        // Plain border — no drop shadow effect so Prism never allocates an off-screen render surface
        setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-width: 1px;");

        imageView.setFitWidth(displayW);
        imageView.setFitHeight(displayH);

        // Lazily create/attach the Canvas only when the page is actually in view
        ensureCanvas(displayW, displayH);
        redrawOverlay();
    }

    /** Ensures the overlay Canvas exists and is attached to the scene graph. */
    private void ensureCanvas(double w, double h) {
        if (overlayCanvas == null) {
            overlayCanvas = new Canvas(w, h);
            overlayCanvas.setMouseTransparent(true);
            getChildren().add(overlayCanvas);
        } else {
            overlayCanvas.setWidth(w);
            overlayCanvas.setHeight(h);
        }
        overlayCanvas.toFront();
    }

    public void setAnnotations(List<PdfAnnotation> annots) {
        annotations.clear();
        pageContent = null;
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
        pageContent = null;
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

    /**
     * Hit tests digital signatures on this page at normalized coordinates.
     */
    public PdfSignatureInfo hitTestSignature(double normX, double normY) {
        if (!pageSignatures.isEmpty()) {
            for (PdfSignatureInfo s : pageSignatures) {
                double[] r = signatureRect(s);
                if (normX >= r[0] && normX <= r[0] + r[2] && normY >= r[1] && normY <= r[1] + r[3]) {
                    return s;
                }
            }
        }
        if (pageContent != null) {
            PdfPageContent.PageObject sigObj = pageContent.hitTestSignature(normX, normY);
            if (sigObj != null && sigObj.payload instanceof PdfSignatureInfo sig) {
                return sig;
            }
        }
        return null;
    }

    public void redrawOverlay() {
        // Canvas might not exist yet (page not loaded) or was removed on unload
        if (overlayCanvas == null) {
            double w = getWidth() > 0 ? getWidth() : getPrefWidth();
            double h = getHeight() > 0 ? getHeight() : getPrefHeight();
            if (w > 0 && h > 0) {
                ensureCanvas(w, h);
            } else {
                return;
            }
        }

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        double pageW = overlayCanvas.getWidth();
        double pageH = overlayCanvas.getHeight();
        if (pageW <= 0 || pageH <= 0) return;

        gc.clearRect(0, 0, pageW, pageH);

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

        // 4. Draw active line-by-line text selection highlights
        if (!selectedLineRects.isEmpty()) {
            gc.setFill(Color.rgb(37, 99, 235, 0.35));
            for (double[] rect : selectedLineRects) {
                if (rect != null && rect.length >= 4 && rect[2] > 0 && rect[3] > 0) {
                    double sx = rect[0] * pageW;
                    double sy = rect[1] * pageH;
                    double sw = rect[2] * pageW;
                    double sh = rect[3] * pageH;
                    gc.fillRect(sx, sy, sw, sh);
                }
            }
        } else if (selectionRect != null && selectionRect.length >= 4 && selectionRect[2] > 0 && selectionRect[3] > 0) {
            double sx = selectionRect[0] * pageW;
            double sy = selectionRect[1] * pageH;
            double sw = selectionRect[2] * pageW;
            double sh = selectionRect[3] * pageH;

            // Semi-transparent blue selection fill
            gc.setFill(Color.rgb(37, 99, 235, 0.35));
            gc.fillRect(sx, sy, sw, sh);

            // Crisp selection border
            gc.setStroke(Color.rgb(37, 99, 235, 0.90));
            gc.setLineWidth(1.0);
            gc.strokeRect(sx, sy, sw, sh);
        }

        // 5. Draw active object selection (with outline, 8 handles, and title badge)
        if (selectedObject != null) {
            double ox = selectedObject.normX * pageW;
            double oy = selectedObject.normY * pageH;
            double ow = selectedObject.normW * pageW;
            double oh = selectedObject.normH * pageH;

            // Semi-transparent blue fill
            gc.setFill(Color.rgb(37, 99, 235, 0.12));
            gc.fillRect(ox, oy, ow, oh);

            // Crisp selection border
            gc.setStroke(Color.rgb(37, 99, 235, 0.95));
            gc.setLineWidth(1.5);
            gc.strokeRect(ox, oy, ow, oh);

            // 8 Selection Handles
            double hw = 6;
            double hh = 6;
            double[] hx = { ox, ox + ow / 2, ox + ow, ox, ox + ow, ox, ox + ow / 2, ox + ow };
            double[] hy = { oy, oy, oy, oy + oh / 2, oy + oh / 2, oy + oh, oy + oh, oy + oh };
            for (int k = 0; k < 8; k++) {
                gc.setFill(Color.WHITE);
                gc.fillRect(hx[k] - hw / 2, hy[k] - hh / 2, hw, hh);
                gc.setStroke(Color.rgb(37, 99, 235));
                gc.setLineWidth(1.2);
                gc.strokeRect(hx[k] - hw / 2, hy[k] - hh / 2, hw, hh);
            }

            // Object title badge above the selection
            if (selectedObject.title != null && !selectedObject.title.isBlank()) {
                gc.setFont(Font.font("System", FontWeight.BOLD, 10));
                double textWidth = selectedObject.title.length() * 6.5 + 14;
                double badgeX = ox;
                double badgeY = Math.max(2, oy - 18);
                gc.setFill(Color.rgb(37, 99, 235, 0.90));
                gc.fillRoundRect(badgeX, badgeY, textWidth, 16, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText(selectedObject.title, badgeX + 6, badgeY + 8);
            }
        }
    }

    public void setDocumentSupplier(Supplier<PDDocument> supplier) {
        this.documentSupplier = supplier;
    }

    public void ensurePageContent() {
        if (pageContent == null && documentSupplier != null) {
            PDDocument doc = documentSupplier.get();
            if (doc != null) {
                pageContent = PdfPageContent.extract(doc, pageIndex, pageSignatures, annotations);
            }
        }
    }

    public PdfPageContent getPageContent() {
        return pageContent;
    }

    public void setPageContent(PdfPageContent content) {
        this.pageContent = content;
    }

    public PdfPageContent.PageObject getSelectedObject() {
        return selectedObject;
    }

    public void setSelectedObject(PdfPageContent.PageObject obj) {
        this.selectedObject = obj;
        this.selectedLineRects.clear();
        this.selectedText = obj != null ? obj.title : null;
        this.selectionRect = obj != null ? obj.getBounds() : null;
        redrawOverlay();
    }

    public List<double[]> getSelectedLineRects() {
        return Collections.unmodifiableList(selectedLineRects);
    }

    public void setSelectedLineRects(List<double[]> rects, String text) {
        this.selectedObject = null;
        this.selectedLineRects.clear();
        if (rects != null) {
            this.selectedLineRects.addAll(rects);
        }
        this.selectedText = text;
        this.selectionRect = !this.selectedLineRects.isEmpty() ? this.selectedLineRects.get(0) : null;
        redrawOverlay();
    }

    public void setOnObjectSelected(Consumer<PdfPageContent.PageObject> callback) {
        this.onObjectSelected = callback;
    }

    public double[] getSelectionRect() {
        if (selectedObject != null) {
            return selectedObject.getBounds();
        }
        if (!selectedLineRects.isEmpty()) {
            return selectedLineRects.get(0);
        }
        return selectionRect;
    }

    public void setSelectionRect(double[] rect) {
        this.selectedObject = null;
        this.selectedLineRects.clear();
        this.selectionRect = rect;
        if (rect != null) {
            this.selectedLineRects.add(rect);
        }
        redrawOverlay();
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String text) {
        this.selectedText = text;
    }

    public void clearSelection() {
        this.selectedObject = null;
        this.selectedLineRects.clear();
        this.selectionRect = null;
        this.selectedText = null;
        redrawOverlay();
    }

    public void setHandToolActive(boolean active) {
        this.handToolActive = active;
        if (active) {
            setCursor(javafx.scene.Cursor.OPEN_HAND);
        } else {
            setCursor(javafx.scene.Cursor.TEXT);
        }
    }

    public boolean isHandToolActive() {
        return handToolActive;
    }

    public void setOnSelectionStarted(Consumer<Integer> callback) {
        this.onSelectionStarted = callback;
    }

    public void setOnSelectionChanged(BiConsumer<Integer, double[]> callback) {
        this.onSelectionChanged = callback;
    }
}
