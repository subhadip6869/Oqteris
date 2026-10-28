package org.oqteris.pdf.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.crypto.signature.PdfSignatureInfo;
import org.oqteris.crypto.signature.SignatureVerificationStatus;
import org.oqteris.pdf.model.PdfAnnotation;
import org.oqteris.pdf.model.PdfBookmarkNode;
import org.oqteris.pdf.model.PdfDocumentModel;
import org.oqteris.pdf.render.PdfPageRenderer;
import org.oqteris.pdf.render.PdfPageView;
import org.oqteris.ui.AsyncExecutor;
import org.oqteris.ui.DocumentView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main UI view for viewing and inspecting PDF documents and digital signatures.
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

    // Navigation and toolbar
    private final TextField pageField = new TextField("1");
    private final Label pageCountLabel = new Label("/ 1");
    private final Label statusPageLabel = new Label("Page 1 of 1");
    private final HBox ribbonBar = new HBox(8);

    // Search bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = new TextField();
    private final Label searchResultLabel = new Label("");
    private int currentMatchIndex = 0;
    private final List<SearchResult> searchResults = new ArrayList<>();

    private double currentZoom = 1.0;
    private int userRotation = 0;
    private int currentPage = 0;

    public PdfDocumentView(File file) throws IOException {
        this.model = new PdfDocumentModel(file);
        this.renderer = new PdfPageRenderer(model.getPdDocument());
        this.signaturePanel = new PdfSignaturePanel(model);

        buildUI();
        loadPages();
    }

    private void buildUI() {
        // Center scroll pane
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setPadding(new Insets(24));
        pagesContainer.getStyleClass().add("editor-canvas-container");

        scrollPane.setContent(pagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("editor-canvas-container");
        setCenter(scrollPane);

        // Sidebar
        setupSidebar();
        setLeft(sideTabPane);

        // Search Bar (Initially hidden)
        setupSearchBar();

        // Ribbon Toolbar
        setupRibbonToolbar();
    }

    private void setupSidebar() {
        sideTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabPane.setPrefWidth(280);

        // 1. Thumbnails Tab
        ScrollPane thumbScroll = new ScrollPane(thumbnailsBox);
        thumbScroll.setFitToWidth(true);
        thumbnailsBox.setPadding(new Insets(10));
        thumbnailsBox.setAlignment(Pos.TOP_CENTER);
        Tab thumbTab = new Tab("Thumbnails", thumbScroll);

        // 2. Bookmarks Tab
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
        searchBar.setPadding(new Insets(6, 12, 6, 12));
        searchBar.setStyle("-fx-background-color: #f3f3f3; -fx-border-color: #dddddd; -fx-border-width: 0 0 1 0;");
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField.setPromptText("Find text in PDF...");
        searchField.setPrefWidth(200);
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                performSearch(searchField.getText(), true);
            } else if (e.getCode() == KeyCode.ESCAPE) {
                toggleSearch(false);
            }
        });

        Button nextBtn = new Button("Next");
        nextBtn.setOnAction(e -> navigateSearch(1));

        Button prevBtn = new Button("Previous");
        prevBtn.setOnAction(e -> navigateSearch(-1));

        Button closeBtn = new Button("✕");
        closeBtn.setOnAction(e -> toggleSearch(false));

        searchBar.getChildren().addAll(new Label("Find:"), searchField, nextBtn, prevBtn, searchResultLabel, closeBtn);
    }

    private void setupRibbonToolbar() {
        ribbonBar.setAlignment(Pos.CENTER_LEFT);
        ribbonBar.setSpacing(6);

        // Navigation Group
        Button firstBtn = new Button("|<");
        firstBtn.setTooltip(new Tooltip("First Page"));
        firstBtn.setOnAction(e -> jumpToPage(0));

        Button prevBtn = new Button("<");
        prevBtn.setTooltip(new Tooltip("Previous Page"));
        prevBtn.setOnAction(e -> jumpToPage(currentPage - 1));

        pageField.setPrefWidth(45);
        pageField.setAlignment(Pos.CENTER);
        pageField.setOnAction(e -> {
            try {
                int p = Integer.parseInt(pageField.getText().trim()) - 1;
                jumpToPage(p);
            } catch (Exception ignored) {
            }
        });

        pageCountLabel.setText("/ " + model.getPageCount());

        Button nextBtn = new Button(">");
        nextBtn.setTooltip(new Tooltip("Next Page"));
        nextBtn.setOnAction(e -> jumpToPage(currentPage + 1));

        Button lastBtn = new Button(">|");
        lastBtn.setTooltip(new Tooltip("Last Page"));
        lastBtn.setOnAction(e -> jumpToPage(model.getPageCount() - 1));

        HBox navGroup = new HBox(4, firstBtn, prevBtn, pageField, pageCountLabel, nextBtn, lastBtn);
        navGroup.setAlignment(Pos.CENTER_LEFT);

        // Zoom Group
        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setOnAction(e -> setZoom(currentZoom - 0.2));

        Button zoomInBtn = new Button("+");
        zoomInBtn.setOnAction(e -> setZoom(currentZoom + 0.2));

        Button fitWidthBtn = new Button("Fit Width");
        fitWidthBtn.setOnAction(e -> setZoom(1.3));

        Button fitPageBtn = new Button("Fit Page");
        fitPageBtn.setOnAction(e -> setZoom(1.0));

        Button rotateBtn = new Button("Rotate 90°");
        rotateBtn.setOnAction(e -> {
            userRotation = (userRotation + 90) % 360;
            renderer.clearCache();
            refreshPageViews();
        });

        HBox zoomGroup = new HBox(4, zoomOutBtn, zoomInBtn, fitWidthBtn, fitPageBtn, rotateBtn);
        zoomGroup.setAlignment(Pos.CENTER_LEFT);

        // Tools Group
        Button findBtn = new Button("Find...");
        findBtn.setOnAction(e -> toggleSearch(!searchBar.isVisible()));

        ToggleButton sideToggle = new ToggleButton("Sidebar");
        sideToggle.setSelected(true);
        sideToggle.setOnAction(e -> {
            sideTabPane.setVisible(sideToggle.isSelected());
            sideTabPane.setManaged(sideToggle.isSelected());
        });

        // Digital Signature Summary Badge
        Node sigBadge = createSignatureToolbarBadge();

        ribbonBar.getChildren().addAll(navGroup, new Separator(javafx.geometry.Orientation.VERTICAL),
                zoomGroup, new Separator(javafx.geometry.Orientation.VERTICAL),
                findBtn, sideToggle, new Separator(javafx.geometry.Orientation.VERTICAL),
                sigBadge);
    }

    private Node createSignatureToolbarBadge() {
        List<PdfSignatureInfo> sigs = model.getSignatures();
        if (sigs.isEmpty()) {
            return new Label("Unsigned PDF");
        }

        // Determine highest severity
        boolean allTrusted = true;
        boolean hasInvalid = false;
        boolean hasUntrusted = false;

        for (PdfSignatureInfo s : sigs) {
            if (s.getStatus() == SignatureVerificationStatus.VALID_AND_TRUSTED) {
                // ok
            } else if (s.getStatus() == SignatureVerificationStatus.VALID_BUT_UNTRUSTED) {
                hasUntrusted = true;
                allTrusted = false;
            } else {
                hasInvalid = true;
                allTrusted = false;
            }
        }

        Label badge = new Label();
        if (hasInvalid) {
            badge.setText("✕ Signature Invalid / Modified");
            badge.getStyleClass().add("signature-badge-invalid");
        } else if (hasUntrusted) {
            badge.setText("⚠ Valid Signature (Untrusted CA)");
            badge.getStyleClass().add("signature-badge-warning");
        } else if (allTrusted) {
            badge.setText("✓ Digitally Signed & Trusted");
            badge.getStyleClass().add("signature-badge-trusted");
        }

        badge.setCursor(javafx.scene.Cursor.HAND);
        badge.setOnMouseClicked(e -> {
            sideTabPane.setVisible(true);
            sideTabPane.setManaged(true);
            sideTabPane.getSelectionModel().select(2); // Select Signatures tab
        });

        return badge;
    }

    private void loadPages() {
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

        refreshPageViews();
    }

    private VBox createThumbnail(int index) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setCursor(javafx.scene.Cursor.HAND);
        box.setPadding(new Insets(4));
        box.setStyle("-fx-border-color: #e0e0e0; -fx-background-color: white; -fx-border-radius: 4px;");

        ImageView iv = new ImageView();
        iv.setFitWidth(100);
        iv.setPreserveRatio(true);

        Label lbl = new Label("Page " + (index + 1));
        lbl.setStyle("-fx-font-size: 10px;");

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

    private void refreshPageViews() {
        for (int i = 0; i < pageViews.size(); i++) {
            final int pageIdx = i;
            final PdfPageView pv = pageViews.get(i);

            AsyncExecutor.execute(
                    () -> renderer.renderPage(pageIdx, currentZoom, userRotation),
                    img -> {
                        if (img != null) {
                            pv.updatePageImage(img);
                            pv.setAnnotations(model.getAnnotations());
                        }
                    },
                    null
            );
        }
    }

    public void jumpToPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= model.getPageCount()) return;
        this.currentPage = pageIndex;
        pageField.setText(String.valueOf(currentPage + 1));
        statusPageLabel.setText("Page " + (currentPage + 1) + " of " + model.getPageCount());

        if (currentPage < pageViews.size()) {
            PdfPageView target = pageViews.get(currentPage);
            double targetY = target.getBoundsInParent().getMinY();
            double totalHeight = pagesContainer.getBoundsInLocal().getHeight();
            if (totalHeight > 0) {
                scrollPane.setVvalue(targetY / totalHeight);
            }
        }
    }

    private void toggleSearch(boolean visible) {
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible) {
            setTop(new VBox(searchBar));
            searchField.requestFocus();
        } else {
            setTop(null);
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
        return ribbonBar;
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
