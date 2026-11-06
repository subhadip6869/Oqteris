package org.originsd.oqteris.pdf.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.controlsfx.control.textfield.TextFields;
import com.pixelduke.control.Ribbon;
import org.kordamp.ikonli.materialdesign2.*;
import org.originsd.oqteris.core.model.DocumentModel;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.pdf.model.PdfAnnotation;
import org.originsd.oqteris.pdf.model.PdfBookmarkNode;
import org.originsd.oqteris.pdf.model.PdfDocumentModel;
import org.originsd.oqteris.pdf.render.PdfPageRenderer;
import org.originsd.oqteris.pdf.render.PdfPageView;
import org.originsd.oqteris.pdf.render.SignatureValidityAppearance;
import org.originsd.oqteris.pdf.ui.ribbon.PdfRibbonBuilder;
import org.originsd.oqteris.ui.AsyncExecutor;
import org.originsd.oqteris.ui.DocumentView;
import org.originsd.oqteris.ui.icon.AppIcons;
import org.originsd.oqteris.ui.ribbon.MainWindowHost;
import org.originsd.oqteris.update.UpdateCheckerService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Main UI view for viewing, annotating, and inspecting PDF documents and digital signatures.
 * Hosts the redesigned professional desktop ribbon.
 */
public class PdfDocumentView extends BorderPane implements DocumentView {
    private final PdfDocumentModel model;
    private final PdfPageRenderer renderer;

    // Center scrollable pages
    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox pagesContainer = new VBox(20);
    private final List<PdfPageView> pageViews = new ArrayList<>();

    // Left sidebar
    private final TabPane sideTabPane = new TabPane();
    private final VBox thumbnailsBox = new VBox(10);
    private final TreeView<PdfBookmarkNode> bookmarksTree = new TreeView<>();
    private final PdfSignaturePanel signaturePanel;

    // Status bar & Ribbon
    private final Label statusPageLabel = new Label("Page 1 of 1");
    private final Ribbon ribbon = new Ribbon();
    private PdfRibbonBuilder ribbonBuilder;
    private MainWindowHost windowHost;

    // Search bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = TextFields.createClearableTextField();
    private final Label searchResultLabel = new Label("");
    private int currentMatchIndex = 0;
    private final List<SearchResult> searchResults = new ArrayList<>();

    private double currentZoom = 1.0;
    private int userRotation = 0;
    private int currentPage = 0;
    private boolean singlePageView = false;
    private boolean handToolActive = false;
    private double lastDragX, lastDragY;

    // Signatures whose appearance layers were redrawn with the real validity state, and the
    // verification results they were computed from (to detect re-verification).
    private Set<PdfSignatureInfo> layeredSignatures = Collections.emptySet();
    private List<PdfSignatureInfo> verifiedSnapshot = new ArrayList<>();

    public PdfDocumentView(File file) throws IOException {
        this(file, null);
    }

    public PdfDocumentView(File file, String password) throws IOException {
        this(new PdfDocumentModel(file, password));
    }

    public PdfDocumentView(PdfDocumentModel model) {
        this.model = model;
        this.renderer = new PdfPageRenderer(model.getPdDocument());
        this.signaturePanel = new PdfSignaturePanel(model);

        // Re-verification (e.g. after "Trust Signer...") creates new PdfSignatureInfo objects:
        // redraw the signature appearance layers and re-render the pages.
        model.addDocumentListener(doc -> Platform.runLater(() -> {
            if (signaturesChanged()) {
                applyValidityAppearance();
                renderer.clearCache();
                refreshPageViews();
            }
            updateSignatureBadge();
        }));

        applyValidityAppearance();   // before the first render, so the first paint is already correct
        buildUI();
        loadPages();
    }

    private boolean signaturesChanged() {
        List<PdfSignatureInfo> cur = model.getSignatures();
        if (cur.size() != verifiedSnapshot.size()) return true;
        for (int i = 0; i < cur.size(); i++) {
            if (cur.get(i) != verifiedSnapshot.get(i)) return true;
        }
        return false;
    }

    /**
     * Rewrites the validity layers (n1 / n3 / n4) of layered signature appearances in the in-memory
     * document at display time. The file on disk is not modified.
     */
    private void applyValidityAppearance() {
        verifiedSnapshot = new ArrayList<>(model.getSignatures());
        layeredSignatures = SignatureValidityAppearance.apply(model.getPdDocument(), verifiedSnapshot);
    }

    private void buildUI() {
        // Center scroll pane
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setPadding(new Insets(24));
        pagesContainer.getStyleClass().add("editor-canvas-container");
        pagesContainer.setPrefWidth(USE_COMPUTED_SIZE);

        scrollPane.setContent(pagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("editor-canvas-container");

        setupHandToolInteractions();
        setCenter(scrollPane);

        // Sidebar
        setupSidebar();
        setLeft(sideTabPane);

        // Search Bar (Initially hidden, placed in top via topArea VBox)
        setupSearchBar();
        VBox topArea = new VBox();
        topArea.getChildren().add(searchBar);
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        setTop(topArea);

        // Redesigned Professional Ribbon
        ribbonBuilder = new PdfRibbonBuilder(this, model, ribbon);
        ribbonBuilder.buildRibbon();
    }

    private void setupHandToolInteractions() {
        scrollPane.setOnMousePressed(e -> {
            if (handToolActive) {
                lastDragX = e.getSceneX();
                lastDragY = e.getSceneY();
                scrollPane.setCursor(javafx.scene.Cursor.CLOSED_HAND);
                pagesContainer.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        scrollPane.setOnMouseDragged(e -> {
            if (handToolActive) {
                double deltaY = e.getSceneY() - lastDragY;
                double contentHeight = pagesContainer.getBoundsInLocal().getHeight();
                if (contentHeight > 0) {
                    scrollPane.setVvalue(scrollPane.getVvalue() - (deltaY / contentHeight));
                }
                lastDragX = e.getSceneX();
                lastDragY = e.getSceneY();
            }
        });

        scrollPane.setOnMouseReleased(e -> {
            if (handToolActive) {
                scrollPane.setCursor(javafx.scene.Cursor.OPEN_HAND);
                pagesContainer.setCursor(javafx.scene.Cursor.OPEN_HAND);
            }
        });
    }

    public void setHandToolActive(boolean active) {
        this.handToolActive = active;
        if (active) {
            scrollPane.setCursor(javafx.scene.Cursor.OPEN_HAND);
            pagesContainer.setCursor(javafx.scene.Cursor.OPEN_HAND);
        } else {
            scrollPane.setCursor(javafx.scene.Cursor.DEFAULT);
            pagesContainer.setCursor(javafx.scene.Cursor.TEXT);
        }
    }

    public MainWindowHost getWindowHost() {
        if (windowHost != null) return windowHost;
        return new MainWindowHost() {
            @Override public void showOpenDialog() {}
            @Override public void saveActiveDocument(boolean saveAs) {}
            @Override public void printActiveDocument() { print(); }
            @Override public void closeActiveDocument() { close(); }
            @Override public void showSettings() {}
            @Override public void showAbout() {}
            @Override public void showCertificateManager() {
                Stage stg = (getScene() != null && getScene().getWindow() instanceof Stage s) ? s : null;
                new org.originsd.oqteris.crypto.trust.CertificateManagerDialog(stg).showAndWait();
            }
            @Override public void setDefaultViewer() {}
            @Override public void toggleFullScreen() {
                if (getScene() != null && getScene().getWindow() instanceof Stage s) {
                    s.setFullScreen(!s.isFullScreen());
                }
            }
        };
    }

    public void setWindowHost(MainWindowHost windowHost) {
        this.windowHost = windowHost;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    private void setupSidebar() {
        sideTabPane.getStyleClass().addAll("sidebar-panel", "sidebar-tab-pane");
        sideTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabPane.setPrefWidth(280);

        // 1. Thumbnails Tab
        ScrollPane thumbScroll = new ScrollPane(thumbnailsBox);
        thumbScroll.getStyleClass().addAll("sidebar-scroll-pane", "thumbnails-scroll-pane");
        thumbScroll.setFitToWidth(true);
        thumbnailsBox.getStyleClass().add("thumbnails-box");
        thumbnailsBox.setPadding(new Insets(10));
        thumbnailsBox.setAlignment(Pos.TOP_CENTER);
        Tab thumbTab = new Tab("Thumbnails", thumbScroll);

        // 2. Bookmarks Tab
        bookmarksTree.getStyleClass().add("sidebar-tree-view");
        Tab bookmarksTab = new Tab("Bookmarks", bookmarksTree);
        bookmarksTree.setShowRoot(false);
        if (model.getRootBookmark() != null) {
            TreeItem<PdfBookmarkNode> rootItem = buildTreeItem(model.getRootBookmark());
            bookmarksTree.setRoot(rootItem);
        }
        bookmarksTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                jumpToPage(newVal.getValue().getPageIndex());
            }
        });

        // 3. Digital Signatures Tab
        Tab sigTab = new Tab("Signatures (" + model.getSignatures().size() + ")", signaturePanel);

        sideTabPane.getTabs().addAll(thumbTab, bookmarksTab, sigTab);

        // Auto-select signatures tab if document is signed!
        if (!model.getSignatures().isEmpty()) {
            sideTabPane.getSelectionModel().select(sigTab);
        }
    }

    private TreeItem<PdfBookmarkNode> buildTreeItem(PdfBookmarkNode node) {
        TreeItem<PdfBookmarkNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (PdfBookmarkNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private void setupSearchBar() {
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5, 12, 5, 12));
        searchBar.getStyleClass().add("editor-search-bar");
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField.setPromptText("Find text in PDF document...");
        searchField.setPrefWidth(220);
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                performSearch(searchField.getText(), true);
            } else if (e.getCode() == KeyCode.ESCAPE) {
                toggleSearch(false);
            }
        });

        Button prevBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_LEFT, "Previous Match", e -> navigateSearch(-1));
        Button nextBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_RIGHT, "Next Match", e -> navigateSearch(1));
        Button closeBtn = AppIcons.iconButton(MaterialDesignC.CLOSE, "Close Search", e -> toggleSearch(false));
        closeBtn.setStyle("-fx-background-color: transparent;");

        searchResultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 500;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchBar.getChildren().addAll(new Label("Find:"), searchField, prevBtn, nextBtn, searchResultLabel, spacer, closeBtn);
    }

    public void updateSignatureBadge() {
        if (ribbonBuilder != null) {
            ribbonBuilder.updateSignatureBadges();
            ribbonBuilder.updateContextualTabs();
        }
    }

    /**
     * Hands the current verified signatures to a page view so it can draw the tick / ? / X badge
     * on top of the signature field, and wires the click action to open the Signatures tab.
     */
    private void applySignatures(PdfPageView pv) {
        pv.setSignatures(model.getSignatures(), userRotation, layeredSignatures);
        pv.setOnSignatureClicked(sig -> {
            sideTabPane.setVisible(true);
            sideTabPane.setManaged(true);
            sideTabPane.getSelectionModel().select(2); // Signatures tab
        });
    }

    public void loadPages() {
        int count = model.getPageCount();
        pagesContainer.getChildren().clear();
        pageViews.clear();
        thumbnailsBox.getChildren().clear();

        for (int i = 0; i < count; i++) {
            PdfPageView pageView = new PdfPageView(i);
            pageViews.add(pageView);
            pagesContainer.getChildren().add(pageView);

            // Thumbnail container
            VBox thumbContainer = createThumbnail(i);
            thumbnailsBox.getChildren().add(thumbContainer);
        }

        if (ribbonBuilder != null) {
            ribbonBuilder.getPageCountLabel().setText("/ " + count);
        }
        refreshPageViews();
    }

    private VBox createThumbnail(int index) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setCursor(javafx.scene.Cursor.HAND);
        box.setPadding(new Insets(4));
        box.getStyleClass().add("thumbnail-item");

        ImageView iv = new ImageView();
        iv.setFitWidth(100);
        iv.setPreserveRatio(true);

        Label lbl = new Label("Page " + (index + 1));
        lbl.getStyleClass().add("thumbnail-label");

        box.getChildren().addAll(iv, lbl);
        box.setOnMouseClicked(e -> jumpToPage(index));

        // Render thumbnail async
        AsyncExecutor.execute(
                () -> renderer.renderThumbnail(index),
                img -> {
                    if (img != null) iv.setImage(img);
                },
                null
        );

        return box;
    }

    public void refreshPageViews() {
        for (int i = 0; i < pageViews.size(); i++) {
            final int pageIdx = i;
            final PdfPageView pv = pageViews.get(i);
            final double zoomAtRequest = currentZoom;
            final int rotationAtRequest = userRotation;

            AsyncExecutor.execute(
                    () -> renderer.renderPage(pageIdx, zoomAtRequest, rotationAtRequest),
                    img -> {
                        if (img != null && zoomAtRequest == currentZoom && rotationAtRequest == userRotation) {
                            pv.updatePageImage(img, zoomAtRequest);
                            pv.setAnnotations(model.getAnnotations());
                            applySignatures(pv);
                        }
                    },
                    null
            );
        }
    }

    public void jumpToPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= model.getPageCount()) return;
        this.currentPage = pageIndex;
        if (ribbonBuilder != null) {
            ribbonBuilder.getPageField().setText(String.valueOf(currentPage + 1));
        }
        statusPageLabel.setText("Page " + (currentPage + 1) + " of " + model.getPageCount());

        if (singlePageView) {
            for (int i = 0; i < pageViews.size(); i++) {
                boolean visible = (i == currentPage);
                pageViews.get(i).setVisible(visible);
                pageViews.get(i).setManaged(visible);
            }
            scrollPane.setVvalue(0);
        } else {
            if (currentPage < pageViews.size()) {
                PdfPageView target = pageViews.get(currentPage);
                double targetY = target.getBoundsInParent().getMinY();
                double totalHeight = pagesContainer.getBoundsInLocal().getHeight();
                if (totalHeight > 0) {
                    scrollPane.setVvalue(targetY / totalHeight);
                }
            }
        }
    }

    // =========================================================================
    // RIBBON ACTION IMPLEMENTATIONS
    // =========================================================================
    public void rotateClockwise() {
        userRotation = (userRotation + 90) % 360;
        renderer.clearCache();
        refreshPageViews();
    }

    public void rotateCounterClockwise() {
        userRotation = (userRotation + 270) % 360;
        renderer.clearCache();
        refreshPageViews();
    }

    public void rotate180() {
        userRotation = (userRotation + 180) % 360;
        renderer.clearCache();
        refreshPageViews();
    }

    public void highlightCurrentPage() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.HIGHLIGHT, currentPage,
                0.15, 0.25, 0.70, 0.04,
                javafx.scene.paint.Color.rgb(254, 240, 138, 0.65), "Highlight"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void underlineCurrentPage() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.UNDERLINE, currentPage,
                0.15, 0.30, 0.70, 0.015,
                javafx.scene.paint.Color.web("#0284c7"), "Underline"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void addStickyNote() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.NOTE, currentPage,
                0.15, 0.15, 0.04, 0.04,
                javafx.scene.paint.Color.web("#f59e0b"), "Sticky Note"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void addTextAnnotation() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.NOTE, currentPage,
                0.20, 0.20, 0.25, 0.06,
                javafx.scene.paint.Color.web("#e0f2fe"), "Text Box"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void addFreehandDrawing() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.FREEHAND, currentPage,
                0.25, 0.45, 0.35, 0.15,
                javafx.scene.paint.Color.web("#ef4444"), "Drawing"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void addStamp(String text) {
        javafx.scene.paint.Color c;
        if ("APPROVED".equalsIgnoreCase(text) || "FINAL".equalsIgnoreCase(text)) {
            c = javafx.scene.paint.Color.web("#10b981");
        } else if ("CONFIDENTIAL".equalsIgnoreCase(text) || "VOID".equalsIgnoreCase(text)) {
            c = javafx.scene.paint.Color.web("#ef4444");
        } else {
            c = javafx.scene.paint.Color.web("#f59e0b");
        }
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.NOTE, currentPage,
                0.30, 0.25, 0.40, 0.12,
                c, text != null ? text : "STAMP"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void addVisualSignature() {
        PdfAnnotation annot = new PdfAnnotation(
                PdfAnnotation.Type.FREEHAND, currentPage,
                0.60, 0.78, 0.30, 0.08,
                javafx.scene.paint.Color.web("#1e293b"), "Visual Signature"
        );
        model.addAnnotation(annot);
        refreshPageViews();
    }

    public void deleteCurrentPage() {
        if (model.getPageCount() <= 1) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Cannot delete the only remaining page in the document.", ButtonType.OK);
            if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
            alert.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete page " + (currentPage + 1) + " of " + model.getPageCount() + "?",
                ButtonType.YES, ButtonType.NO);
        if (getScene() != null && getScene().getWindow() != null) confirm.initOwner(getScene().getWindow());
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            try {
                model.getPdDocument().removePage(currentPage);
                model.setModified(true);
                int newTarget = Math.max(0, currentPage - 1);
                loadPages();
                jumpToPage(newTarget);
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Failed to delete page: " + ex.getMessage(), ButtonType.OK);
                err.showAndWait();
            }
        }
    }

    public void setSinglePageView(boolean single) {
        this.singlePageView = single;
        for (int i = 0; i < pageViews.size(); i++) {
            boolean visible = !single || (i == currentPage);
            pageViews.get(i).setVisible(visible);
            pageViews.get(i).setManaged(visible);
        }
        if (single) {
            scrollPane.setVvalue(0);
        }
    }

    public void selectSidebarTab(int index) {
        sideTabPane.setVisible(true);
        sideTabPane.setManaged(true);
        if (index >= 0 && index < sideTabPane.getTabs().size()) {
            sideTabPane.getSelectionModel().select(index);
        }
    }

    public void toggleSidebar() {
        boolean next = !sideTabPane.isVisible();
        sideTabPane.setVisible(next);
        sideTabPane.setManaged(next);
    }

    public void reverifySignatures() {
        model.reVerifySignatures();
        applyValidityAppearance();
        renderer.clearCache();
        refreshPageViews();
        updateSignatureBadge();
        signaturePanel.refreshSignatures();
    }

    public void showSignatureDetails() {
        selectSidebarTab(2);
    }

    public void showPageProperties() {
        try {
            var page = model.getPage(currentPage);
            var mediaBox = page != null ? page.getMediaBox() : null;
            String w = mediaBox != null ? String.format("%.1f pt (%.1f mm)", mediaBox.getWidth(), mediaBox.getWidth() * 25.4 / 72.0) : "N/A";
            String h = mediaBox != null ? String.format("%.1f pt (%.1f mm)", mediaBox.getHeight(), mediaBox.getHeight() * 25.4 / 72.0) : "N/A";
            int rot = page != null ? page.getRotation() : 0;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Page Properties - Oqteris");
            alert.setHeaderText("Page " + (currentPage + 1) + " of " + model.getPageCount());
            alert.setContentText(
                    "Width: " + w + "\n" +
                    "Height: " + h + "\n" +
                    "Intrinsic Rotation: " + rot + "°\n" +
                    "Display User Rotation: " + userRotation + "°\n" +
                    "Total Pages: " + model.getPageCount()
            );
            if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
            alert.showAndWait();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Unable to read page properties: " + ex.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }

    public void showDocumentProperties() {
        var info = model.getPdDocument().getDocumentInformation();
        String title = (info != null && info.getTitle() != null) ? info.getTitle() : model.getTitle();
        String author = (info != null && info.getAuthor() != null) ? info.getAuthor() : "Not specified";
        String subject = (info != null && info.getSubject() != null) ? info.getSubject() : "Not specified";
        String producer = (info != null && info.getProducer() != null) ? info.getProducer() : "Apache PDFBox / Oqteris";
        float ver = model.getPdDocument().getVersion();
        boolean enc = model.isEncrypted();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Document Properties - Oqteris");
        alert.setHeaderText(title);
        alert.setContentText(
                "Author: " + author + "\n" +
                "Subject: " + subject + "\n" +
                "PDF Version: " + ver + "\n" +
                "Page Count: " + model.getPageCount() + "\n" +
                "Encrypted: " + (enc ? "Yes" : "No") + "\n" +
                "Producer: " + producer + "\n" +
                "File: " + (model.getFile() != null ? model.getFile().getAbsolutePath() : "In-Memory")
        );
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void showEncryptionStatus() {
        boolean enc = model.isEncrypted();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Document Security - Oqteris");
        alert.setHeaderText(enc ? "Encrypted Document" : "Standard Security (Unencrypted)");
        alert.setContentText(enc ?
                "This document is encrypted with password protection.\nSecurity Handler: Standard Encryption" :
                "This document is not password protected.\nAll reading and annotation capabilities are unrestricted."
        );
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void showPermissionsStatus() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Document Permissions - Oqteris");
        alert.setHeaderText("Access Permissions");
        alert.setContentText(
                "Printing: Allowed\n" +
                "Content Copying: Allowed\n" +
                "Annotations & Comments: Allowed\n" +
                "Form Filling: Allowed\n" +
                "Page Extraction: Allowed"
        );
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void showFormNotice() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Interactive Forms - Oqteris");
        alert.setHeaderText("AcroForm Fields");
        alert.setContentText("Form fields in this document can be clicked and edited directly on the page surface.");
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void clearFormFields() {
        try {
            var catalog = model.getPdDocument().getDocumentCatalog();
            var acroForm = catalog != null ? catalog.getAcroForm() : null;
            if (acroForm != null) {
                for (var field : acroForm.getFields()) {
                    field.setValue("");
                }
                model.setModified(true);
                refreshPageViews();
            }
        } catch (Exception ignored) {}
    }

    public void emailDocument() {
        if (model.getFile() != null && java.awt.Desktop.isDesktopSupported()) {
            try {
                java.awt.Desktop.getDesktop().mail(new java.net.URI("mailto:?subject=" +
                        java.net.URLEncoder.encode("PDF: " + model.getTitle(), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception ignored) {}
        }
    }

    public void copyFilePath() {
        if (model.getFile() != null) {
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(model.getFile().getAbsolutePath());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        }
    }

    public void openContainingFolder() {
        if (model.getFile() != null && java.awt.Desktop.isDesktopSupported()) {
            try {
                java.awt.Desktop.getDesktop().open(model.getFile().getParentFile());
            } catch (Exception ignored) {}
        }
    }

    public void showKeyboardShortcuts() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts - Oqteris");
        alert.setHeaderText("Oqteris PDF Shortcuts");
        alert.setContentText(
                "Ctrl+O: Open PDF\n" +
                "Ctrl+S: Save Document\n" +
                "Ctrl+Shift+S: Save As\n" +
                "Ctrl+P: Print\n" +
                "Ctrl+F: Find Text\n" +
                "Ctrl+0: Actual Size (100%)\n" +
                "Ctrl++: Zoom In\n" +
                "Ctrl+-: Zoom Out\n" +
                "F11: Full Screen Mode\n" +
                "Ctrl+C: Copy Text\n" +
                "Ctrl+A: Select All Text\n" +
                "Home / End: First / Last Page\n" +
                "Page Up / Page Down: Previous / Next Page"
        );
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void showDocumentation() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Oqteris Documentation");
        alert.setHeaderText("Oqteris PDF Reader & Editor");
        alert.setContentText(
                "Oqteris is a dedicated desktop PDF reader and editor.\n\n" +
                "Key features:\n" +
                "• Professional Desktop Ribbon Interface\n" +
                "• Digital Signature Verification and X.509 Trust Management\n" +
                "• High-resolution Page Rendering with Async Cache\n" +
                "• Markup & Comments: Highlights, Underlines, Sticky Notes, Stamps\n" +
                "• Full search and outline bookmark navigation\n" +
                "• Light and Dark desktop themes"
        );
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    public void showUpdateNotice() {
        Stage stage = (getScene() != null && getScene().getWindow() instanceof Stage s) ? s : null;
        UpdateCheckerService.getInstance().checkForUpdatesAsync(stage, true);
    }

    public void showDisabledFeatureNotice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title + " - Oqteris");
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    private void toggleSearch(boolean visible) {
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible) {
            searchField.requestFocus();
        } else {
            clearSearch();
        }
    }

    private void performSearch(String query, boolean jumpToFirst) {
        if (query == null || query.isBlank()) {
            clearSearch();
            return;
        }

        searchResults.clear();
        AsyncExecutor.execute(
                () -> {
                    List<SearchResult> results = new ArrayList<>();
                    try {
                        PDFTextStripper stripper = new PDFTextStripper();
                        for (int p = 0; p < model.getPageCount(); p++) {
                            stripper.setStartPage(p + 1);
                            stripper.setEndPage(p + 1);
                            String text = stripper.getText(model.getPdDocument());
                            int idx = 0;
                            while ((idx = text.toLowerCase().indexOf(query.toLowerCase(), idx)) != -1) {
                                results.add(new SearchResult(p, idx, query.length()));
                                idx += query.length();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    return results;
                },
                results -> {
                    searchResults.addAll(results);
                    if (!results.isEmpty()) {
                        currentMatchIndex = 0;
                        searchResultLabel.setText("Match 1 of " + results.size());
                        if (jumpToFirst) {
                            jumpToPage(results.get(0).pageIndex);
                        }
                    } else {
                        searchResultLabel.setText("No matches found");
                    }
                },
                null
        );
    }

    private void navigateSearch(int direction) {
        if (searchResults.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + direction + searchResults.size()) % searchResults.size();
        searchResultLabel.setText("Match " + (currentMatchIndex + 1) + " of " + searchResults.size());
        jumpToPage(searchResults.get(currentMatchIndex).pageIndex);
    }

    private void clearSearch() {
        searchResults.clear();
        searchResultLabel.setText("");
        for (PdfPageView pv : pageViews) {
            pv.setSearchHighlights(null);
        }
    }

    private static class SearchResult {
        final int pageIndex;
        final int charIndex;
        final int length;

        SearchResult(int pageIndex, int charIndex, int length) {
            this.pageIndex = pageIndex;
            this.charIndex = charIndex;
            this.length = length;
        }
    }

    @Override
    public DocumentModel getDocument() {
        return model;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbon;
    }

    @Override
    public Node getStatusBarNode() {
        return statusPageLabel;
    }

    @Override
    public double getZoom() {
        return currentZoom;
    }

    @Override
    public void setZoom(double zoom) {
        this.currentZoom = Math.max(0.25, Math.min(4.0, zoom));
        refreshPageViews();
    }

    @Override
    public void showSearch() {
        toggleSearch(true);
    }

    @Override
    public void copy() {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(currentPage + 1);
            stripper.setEndPage(currentPage + 1);
            String pageText = stripper.getText(model.getPdDocument());
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(pageText);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void selectAll() {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(model.getPdDocument());
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(fullText);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void print() {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene() != null ? getScene().getWindow() : null)) {
            boolean success = true;
            for (int p = 0; p < model.getPageCount(); p++) {
                try {
                    Image pageImg = renderer.renderPage(p, 2.0, userRotation);
                    ImageView iv = new ImageView(pageImg);
                    if (!job.printPage(iv)) {
                        success = false;
                        break;
                    }
                } catch (Exception ex) {
                    success = false;
                    break;
                }
            }
            if (success) {
                job.endJob();
            }
        }
    }

    @Override
    public void close() {
        try {
            model.close();
        } catch (Exception ignored) {
        }
    }
}