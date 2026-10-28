package org.oqteris.writer.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.application.Platform;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.TextFields;
import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import org.kordamp.ikonli.materialdesign2.*;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.core.render.FontManager;
import org.oqteris.ui.DocumentView;
import org.oqteris.ui.icon.AppIcons;
import org.oqteris.writer.layout.LayoutEngine;
import org.oqteris.writer.layout.LayoutLine;
import org.oqteris.writer.layout.LayoutPage;
import org.oqteris.writer.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main UI view and interactive multi-page canvas for Oqteris Writer.
 */
public class WriterDocumentView extends BorderPane implements DocumentView {
    private final WriterDocument document;
    private final List<LayoutPage> pages = new ArrayList<>();

    // Multi-page canvas
    private final Canvas canvas = new Canvas(800, 1000);
    private final ScrollPane scrollPane = new ScrollPane();
    private final StackPane canvasContainer = new StackPane();

    // Ribbon toolbar
    private final Ribbon ribbon = new Ribbon();
    private final Label statusStatsLabel = new Label("Page 1 of 1 | Words: 0");

    // Caret & selection state
    private int currentElementIndex = 0;
    private int currentRunIndex = 0;
    private int currentCharOffset = 0;

    private boolean hasSelection = false;
    private int selStartElem = 0, selStartRun = 0, selStartChar = 0;
    private int selEndElem = 0, selEndRun = 0, selEndChar = 0;

    private boolean caretVisible = true;
    private double caretX = 72;
    private double caretY = 72;
    private double caretHeight = 14;
    private Timeline caretBlink;
    private double currentZoom = 1.0;

    // Search and Replace Bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = TextFields.createClearableTextField();
    private final TextField replaceField = TextFields.createClearableTextField();
    private final Label searchResultLabel = new Label("");
    private final List<SearchResult> searchResults = new ArrayList<>();
    private int currentMatchIndex = 0;

    public WriterDocumentView() {
        this(new WriterDocument());
    }

    public WriterDocumentView(WriterDocument document) {
        this.document = document;
        buildUI();
        relayout();
        setupCaretBlink();
    }

    private void buildUI() {
        // Canvas Container
        canvasContainer.setAlignment(Pos.TOP_CENTER);
        canvasContainer.setPadding(new Insets(24));
        canvasContainer.getStyleClass().add("editor-canvas-container");
        canvasContainer.getChildren().add(canvas);

        scrollPane.setContent(canvasContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("editor-canvas-container");
        setCenter(scrollPane);

        // Canvas events
        canvas.setFocusTraversable(true);
        canvas.setOnMousePressed(this::handleCanvasPressed);
        canvas.setOnMouseDragged(this::handleCanvasDragged);
        canvas.setOnMouseReleased(this::handleCanvasReleased);
        canvas.setOnKeyPressed(this::handleKeyPressed);
        canvas.setOnKeyTyped(this::handleKeyTyped);

        // Ribbon Toolbar
        setupRibbonToolbar();

        // Search Bar setup
        setupSearchBar();
    }

    private void setupCaretBlink() {
        caretBlink = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            caretVisible = !caretVisible;
            render();
        }));
        caretBlink.setCycleCount(Animation.INDEFINITE);
        caretBlink.play();
    }

    private void setupSearchBar() {
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(6, 12, 6, 12));
        searchBar.getStyleClass().add("editor-search-bar");
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField.setPromptText("Find text in document...");
        searchField.setPrefWidth(210);
        searchField.setOnAction(e -> performSearch(searchField.getText(), true));

        replaceField.setPromptText("Replace with...");
        replaceField.setPrefWidth(210);

        Button findPrevBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_LEFT, "Previous Match", e -> navigateSearch(-1));
        Button findNextBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_RIGHT, "Next Match", e -> navigateSearch(1));

        Button replaceBtn = AppIcons.button("Replace", MaterialDesignF.FIND_REPLACE, e -> performReplace(false));
        Button replaceAllBtn = new Button("Replace All");
        replaceAllBtn.setOnAction(e -> performReplace(true));

        Button closeSearchBtn = AppIcons.iconButton(MaterialDesignC.CLOSE, "Close Search", e -> toggleSearch(false));
        closeSearchBtn.setStyle("-fx-background-color: transparent;");

        searchResultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 500;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchBar.getChildren().addAll(
                new Label("Find:"), searchField,
                new Label("Replace:"), replaceField,
                findPrevBtn, findNextBtn, replaceBtn, replaceAllBtn,
                searchResultLabel, spacer, closeSearchBtn
        );
    }

    private void setupRibbonToolbar() {
        ribbon.getTabs().clear();
        ribbon.setMaxWidth(Double.MAX_VALUE);

        // =========================================================================
        // Tab 1: HOME
        // =========================================================================
        RibbonTab homeTab = new RibbonTab("Home");

        // Group: Clipboard
        RibbonGroup clipboardGroup = new RibbonGroup();
        clipboardGroup.setTitle("Clipboard");
        Button pasteBtn = AppIcons.button("Paste", MaterialDesignC.CONTENT_PASTE, e -> paste());
        Button cutBtn = AppIcons.button("Cut", MaterialDesignC.CONTENT_CUT, e -> cut());
        Button copyBtn = AppIcons.button("Copy", MaterialDesignC.CONTENT_COPY, e -> copy());
        clipboardGroup.getNodes().addAll(pasteBtn, cutBtn, copyBtn);

        // Group: Font
        RibbonGroup fontGroup = new RibbonGroup();
        fontGroup.setTitle("Font");

        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("Segoe UI", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia", "Verdana");
        fontCombo.setValue("Segoe UI");
        fontCombo.setPrefWidth(125);
        fontCombo.setOnAction(e -> applyRunStyle(r -> r.setFontFamily(fontCombo.getValue())));

        ComboBox<Double> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll(8.0, 9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0, 48.0, 72.0);
        sizeCombo.setValue(11.0);
        sizeCombo.setPrefWidth(70);
        sizeCombo.setOnAction(e -> applyRunStyle(r -> r.setFontSize(sizeCombo.getValue())));

        ToggleButton boldBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_BOLD, "Bold (Ctrl+B)");
        boldBtn.setOnAction(e -> applyRunStyle(r -> r.setBold(boldBtn.isSelected())));

        ToggleButton italicBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_ITALIC, "Italic (Ctrl+I)");
        italicBtn.setOnAction(e -> applyRunStyle(r -> r.setItalic(italicBtn.isSelected())));

        ToggleButton underlineBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_UNDERLINE, "Underline (Ctrl+U)");
        underlineBtn.setOnAction(e -> applyRunStyle(r -> r.setUnderline(underlineBtn.isSelected())));

        ToggleButton strikeBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_STRIKETHROUGH, "Strikethrough");
        strikeBtn.setOnAction(e -> applyRunStyle(r -> r.setStrikethrough(strikeBtn.isSelected())));

        ColorPicker textColor = new ColorPicker(Color.BLACK);
        textColor.setPrefWidth(46);
        textColor.setTooltip(new Tooltip("Text Color"));
        textColor.setOnAction(e -> applyRunStyle(r -> r.setTextColor(textColor.getValue())));

        ColorPicker highlightColor = new ColorPicker(Color.TRANSPARENT);
        highlightColor.setPrefWidth(46);
        highlightColor.setTooltip(new Tooltip("Highlight Color"));
        highlightColor.setOnAction(e -> applyRunStyle(r -> r.setHighlightColor(highlightColor.getValue())));

        fontGroup.getNodes().addAll(fontCombo, sizeCombo, boldBtn, italicBtn, underlineBtn, strikeBtn, textColor, highlightColor);

        // Group: Paragraph
        RibbonGroup paragraphGroup = new RibbonGroup();
        paragraphGroup.setTitle("Paragraph");

        ToggleButton alignLeft = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_LEFT, "Align Left");
        alignLeft.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.LEFT));

        ToggleButton alignCenter = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_CENTER, "Center");
        alignCenter.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.CENTER));

        ToggleButton alignRight = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_RIGHT, "Align Right");
        alignRight.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.RIGHT));

        ToggleButton alignJustify = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_JUSTIFY, "Justify");
        alignJustify.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.JUSTIFY));

        SegmentedButton alignSegmented = new SegmentedButton(alignLeft, alignCenter, alignRight, alignJustify);

        ToggleButton bulletBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_LIST_BULLETED, "Bulleted List");
        bulletBtn.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.BULLET ? Paragraph.ListType.NONE : Paragraph.ListType.BULLET);
                document.setModified(true);
                relayout();
            }
        });

        ToggleButton numberBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_LIST_NUMBERED, "Numbered List");
        numberBtn.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.NUMBERED ? Paragraph.ListType.NONE : Paragraph.ListType.NUMBERED);
                document.setModified(true);
                relayout();
            }
        });

        paragraphGroup.getNodes().addAll(alignSegmented, bulletBtn, numberBtn);

        // Group: Styles
        RibbonGroup styleGroup = new RibbonGroup();
        styleGroup.setTitle("Styles");

        ComboBox<Paragraph.HeadingStyle> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(Paragraph.HeadingStyle.values());
        styleCombo.setValue(Paragraph.HeadingStyle.NORMAL);
        styleCombo.setPrefWidth(125);
        styleCombo.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setHeadingStyle(styleCombo.getValue());
                document.setModified(true);
                relayout();
            }
        });
        styleGroup.getNodes().add(styleCombo);

        homeTab.getRibbonGroups().addAll(clipboardGroup, fontGroup, paragraphGroup, styleGroup);

        // =========================================================================
        // Tab 2: INSERT
        // =========================================================================
        RibbonTab insertTab = new RibbonTab("Insert");

        RibbonGroup tableGroup = new RibbonGroup();
        tableGroup.setTitle("Tables");
        Button insertTableBtn = AppIcons.button("Table (3x3)", MaterialDesignT.TABLE, e -> {
            DocumentTable table = new DocumentTable(3, 3);
            document.addElement(table);
            document.addElement(new Paragraph());
            relayout();
        });
        tableGroup.getNodes().add(insertTableBtn);

        RibbonGroup mediaGroup = new RibbonGroup();
        mediaGroup.setTitle("Illustrations");
        Button insertImageBtn = AppIcons.button("Pictures", MaterialDesignI.IMAGE, e -> handleInsertImage());
        mediaGroup.getNodes().add(insertImageBtn);

        RibbonGroup linksGroup = new RibbonGroup();
        linksGroup.setTitle("Links");
        Button insertLinkBtn = AppIcons.button("Hyperlink", MaterialDesignL.LINK, e -> handleInsertLink());
        linksGroup.getNodes().add(insertLinkBtn);

        RibbonGroup pagesGroup = new RibbonGroup();
        pagesGroup.setTitle("Pages");
        Button insertPageBreakBtn = AppIcons.button("Page Break", MaterialDesignP.PAGE_NEXT, e -> {
            document.addElement(new PageBreakElement());
            document.addElement(new Paragraph());
            relayout();
        });
        pagesGroup.getNodes().add(insertPageBreakBtn);

        insertTab.getRibbonGroups().addAll(tableGroup, mediaGroup, linksGroup, pagesGroup);

        // =========================================================================
        // Tab 3: LAYOUT
        // =========================================================================
        RibbonTab layoutTab = new RibbonTab("Layout");

        RibbonGroup pageSetupGroup = new RibbonGroup();
        pageSetupGroup.setTitle("Page Setup");

        ComboBox<String> orientCombo = new ComboBox<>();
        orientCombo.getItems().addAll("Portrait", "Landscape");
        orientCombo.setValue("Portrait");
        orientCombo.setOnAction(e -> {
            document.getPageSetup().setOrientation("Landscape".equals(orientCombo.getValue()) ? PageSetup.Orientation.LANDSCAPE : PageSetup.Orientation.PORTRAIT);
            document.setModified(true);
            relayout();
        });

        ComboBox<String> paperCombo = new ComboBox<>();
        paperCombo.getItems().addAll("A4 (210 x 297 mm)", "Letter (8.5 x 11 in)");
        paperCombo.setValue("A4 (210 x 297 mm)");
        paperCombo.setOnAction(e -> {
            if (paperCombo.getValue() != null && paperCombo.getValue().startsWith("Letter")) {
                document.getPageSetup().setPageWidth(612.0);
                document.getPageSetup().setPageHeight(792.0);
            } else {
                document.getPageSetup().setPageWidth(595.28);
                document.getPageSetup().setPageHeight(841.89);
            }
            document.setModified(true);
            relayout();
        });

        Button pageSetupBtn = AppIcons.button("More Options...", MaterialDesignC.COG, e -> showPageSetupDialog());
        pageSetupGroup.getNodes().addAll(orientCombo, paperCombo, pageSetupBtn);

        layoutTab.getRibbonGroups().add(pageSetupGroup);

        // =========================================================================
        // Tab 4: REVIEW & VIEW
        // =========================================================================
        RibbonTab viewTab = new RibbonTab("Review & View");

        RibbonGroup searchGroup = new RibbonGroup();
        searchGroup.setTitle("Editing");
        Button findBtn = AppIcons.button("Find & Replace", MaterialDesignF.FIND_REPLACE, e -> toggleSearch(!searchBar.isVisible()));
        searchGroup.getNodes().add(findBtn);

        RibbonGroup statsGroup = new RibbonGroup();
        statsGroup.setTitle("Statistics");
        Label wordBadge = AppIcons.badge("Words: " + document.getWordCount(), MaterialDesignT.TEXT_BOX_OUTLINE, "stats-badge");
        document.addDocumentListener(d -> Platform.runLater(() -> wordBadge.setText("Words: " + document.getWordCount())));
        statsGroup.getNodes().add(wordBadge);

        viewTab.getRibbonGroups().addAll(searchGroup, statsGroup);

        ribbon.getTabs().addAll(homeTab, insertTab, layoutTab, viewTab);
    }

    private void handleInsertImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Insert Image - Oqteris Writer");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.gif)", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            try (FileInputStream in = new FileInputStream(file)) {
                Image img = new Image(in);
                double w = Math.min(img.getWidth(), 450);
                double h = img.getHeight() * (w / Math.max(1, img.getWidth()));
                DocumentImage docImg = new DocumentImage(img, w, h);
                document.addElement(docImg);
                document.addElement(new Paragraph());
                document.setModified(true);
                relayout();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to load image: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        }
    }

    private void handleInsertLink() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Insert Hyperlink");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());

        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(20));

        TextField textF = new TextField("Click here");
        TextField urlF = new TextField("https://");
        gp.addRow(0, new Label("Display Text:"), textF);
        gp.addRow(1, new Label("Target URL:"), urlF);

        dialog.getDialogPane().setContent(gp);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Paragraph p = getActiveParagraph();
            if (p == null) {
                p = new Paragraph();
                document.addElement(p);
            }
            TextRun linkRun = new TextRun(textF.getText());
            linkRun.setHyperlinkUrl(urlF.getText());
            linkRun.setTextColor(Color.rgb(37, 99, 235));
            linkRun.setUnderline(true);
            p.addRun(linkRun);
            document.setModified(true);
            relayout();
        }
    }

    private void showPageSetupDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Page Setup - Oqteris Writer");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());

        PageSetup ps = document.getPageSetup();

        GridPane gp = new GridPane();
        gp.setHgap(12);
        gp.setVgap(12);
        gp.setPadding(new Insets(20));

        ComboBox<String> paperCombo = new ComboBox<>();
        paperCombo.getItems().addAll("A4 (210 x 297 mm)", "Letter (8.5 x 11 in)", "Legal (8.5 x 14 in)");
        paperCombo.setValue(ps.getPageWidth() == 612 ? "Letter (8.5 x 11 in)" : "A4 (210 x 297 mm)");

        ComboBox<String> orientCombo = new ComboBox<>();
        orientCombo.getItems().addAll("Portrait", "Landscape");
        orientCombo.setValue(ps.getOrientation() == PageSetup.Orientation.LANDSCAPE ? "Landscape" : "Portrait");

        ComboBox<String> marginCombo = new ComboBox<>();
        marginCombo.getItems().addAll("Normal (1.0 in)", "Narrow (0.5 in)", "Wide (1.5 in)");
        marginCombo.setValue("Normal (1.0 in)");

        gp.addRow(0, new Label("Paper Size:"), paperCombo);
        gp.addRow(1, new Label("Orientation:"), orientCombo);
        gp.addRow(2, new Label("Margins:"), marginCombo);

        dialog.getDialogPane().setContent(gp);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            boolean isLandscape = "Landscape".equals(orientCombo.getValue());
            ps.setOrientation(isLandscape ? PageSetup.Orientation.LANDSCAPE : PageSetup.Orientation.PORTRAIT);

            double m = "Narrow (0.5 in)".equals(marginCombo.getValue()) ? 36.0 :
                    ("Wide (1.5 in)".equals(marginCombo.getValue()) ? 108.0 : 72.0);
            ps.setMargins(m, m, m, m);

            if (paperCombo.getValue() != null && paperCombo.getValue().startsWith("Letter")) {
                ps.setPageWidth(612.0);
                ps.setPageHeight(792.0);
            } else {
                ps.setPageWidth(595.28);
                ps.setPageHeight(841.89);
            }

            document.setModified(true);
            relayout();
        }
    }

    private void applyRunStyle(java.util.function.Consumer<TextRun> modifier) {
        Paragraph p = getActiveParagraph();
        if (p != null && !p.getRuns().isEmpty()) {
            TextRun r = p.getRuns().get(Math.min(currentRunIndex, p.getRuns().size() - 1));
            modifier.accept(r);
            document.setModified(true);
            relayout();
        }
    }

    private void setParagraphAlignment(Paragraph.Alignment align) {
        Paragraph p = getActiveParagraph();
        if (p != null) {
            p.setAlignment(align);
            document.setModified(true);
            relayout();
        }
    }

    private Paragraph getActiveParagraph() {
        if (currentElementIndex >= 0 && currentElementIndex < document.getElements().size()) {
            DocumentElement el = document.getElements().get(currentElementIndex);
            if (el instanceof Paragraph p) return p;
        }
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) return p;
        }
        return null;
    }

    public void relayout() {
        pages.clear();
        pages.addAll(LayoutEngine.layout(document));

        double pageW = document.getPageSetup().getPageWidth() * currentZoom;
        double pageH = document.getPageSetup().getPageHeight() * currentZoom;
        double gap = 24.0 * currentZoom;
        double totalH = pages.size() * (pageH + gap) + gap;

        canvas.setWidth(pageW + 48.0 * currentZoom);
        canvas.setHeight(totalH);

        updateStatus();
        render();
    }

    public void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.save();
        gc.scale(currentZoom, currentZoom);

        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double pageX = (canvas.getWidth() / currentZoom - pageW) / 2.0;
        double currentY = gap;

        for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
            LayoutPage page = pages.get(pIdx);

            // 1. Draw page paper with modern subtle drop shadow
            gc.setFill(Color.rgb(0, 0, 0, 0.12));
            gc.fillRect(pageX + 4, currentY + 4, pageW, pageH);

            gc.setFill(Color.WHITE);
            gc.fillRect(pageX, currentY, pageW, pageH);

            gc.setStroke(Color.rgb(226, 232, 240));
            gc.setLineWidth(1.0);
            gc.strokeRect(pageX, currentY, pageW, pageH);

            // 2. Draw margin guides
            PageSetup setup = page.getPageSetup();
            gc.setStroke(Color.rgb(241, 245, 249));
            gc.strokeRect(pageX + setup.getMarginLeft(), currentY + setup.getMarginTop(),
                    setup.getPrintableWidth(), setup.getPrintableHeight());

            // 3. Draw Header & Footer
            if (page.getHeaderLine() != null) {
                drawLine(gc, page.getHeaderLine(), pageX, currentY);
            }
            if (page.getFooterLine() != null) {
                drawLine(gc, page.getFooterLine(), pageX, currentY);
            }

            // 4. Draw Lines
            for (LayoutLine line : page.getLines()) {
                drawLine(gc, line, pageX, currentY);
            }

            // 5. Draw Tables
            for (LayoutPage.TableBox tBox : page.getTableBoxes()) {
                drawTableBox(gc, tBox, pageX, currentY);
            }

            // 6. Draw Images
            for (LayoutPage.ImageBox imgBox : page.getImageBoxes()) {
                if (imgBox.image != null) {
                    gc.drawImage(imgBox.image, pageX + imgBox.x, currentY + imgBox.y, imgBox.width, imgBox.height);
                }
            }

            currentY += pageH + gap;
        }

        // 7. Draw Caret
        if (caretVisible) {
            gc.setStroke(Color.rgb(30, 41, 59));
            gc.setLineWidth(1.6);
            gc.strokeLine(caretX, caretY, caretX, caretY + caretHeight);
        }

        gc.restore();
    }

    private void drawLine(GraphicsContext gc, LayoutLine line, double pageX, double pageY) {
        gc.setTextBaseline(VPos.BASELINE);
        for (LayoutLine.LineSegment seg : line.getSegments()) {
            gc.setFont(seg.getFont());

            // Highlight background
            if (seg.getRun().getHighlightColor() != null && seg.getRun().getHighlightColor() != Color.TRANSPARENT) {
                gc.setFill(seg.getRun().getHighlightColor());
                gc.fillRect(pageX + line.getX() + seg.getX(), pageY + line.getY(), seg.getWidth(), line.getHeight());
            }

            // Text
            gc.setFill(seg.getRun().getTextColor());
            gc.fillText(seg.getText(), pageX + line.getX() + seg.getX(), pageY + line.getY() + line.getBaseline());

            // Underline / Strikethrough
            if (seg.getRun().isUnderline()) {
                gc.setStroke(seg.getRun().getTextColor());
                gc.setLineWidth(1.0);
                double ulY = pageY + line.getY() + line.getBaseline() + 2;
                gc.strokeLine(pageX + line.getX() + seg.getX(), ulY, pageX + line.getX() + seg.getX() + seg.getWidth(), ulY);
            }
            if (seg.getRun().isStrikethrough()) {
                gc.setStroke(seg.getRun().getTextColor());
                gc.setLineWidth(1.0);
                double stY = pageY + line.getY() + (line.getHeight() / 2.0);
                gc.strokeLine(pageX + line.getX() + seg.getX(), stY, pageX + line.getX() + seg.getX() + seg.getWidth(), stY);
            }
        }
    }

    private void drawTableBox(GraphicsContext gc, LayoutPage.TableBox tBox, double pageX, double pageY) {
        gc.setStroke(tBox.borderColor);
        gc.setLineWidth(tBox.borderWidth);

        for (LayoutPage.TableCellBox cBox : tBox.cells) {
            double cx = pageX + cBox.x;
            double cy = pageY + cBox.y;

            if (cBox.cell.getBackgroundColor() != null) {
                gc.setFill(cBox.cell.getBackgroundColor());
                gc.fillRect(cx, cy, cBox.width, cBox.height);
            }

            gc.strokeRect(cx, cy, cBox.width, cBox.height);

            for (LayoutLine line : cBox.lines) {
                drawLine(gc, line, pageX, pageY);
            }
        }
    }

    private void handleCanvasPressed(MouseEvent e) {
        canvas.requestFocus();
        double unscaledX = e.getX() / currentZoom;
        double unscaledY = e.getY() / currentZoom;

        hitTestCaret(unscaledX, unscaledY);
        hasSelection = false;
        selStartElem = currentElementIndex;
        selStartRun = currentRunIndex;
        selStartChar = currentCharOffset;
        caretVisible = true;
        render();
    }

    private void handleCanvasDragged(MouseEvent e) {
        double unscaledX = e.getX() / currentZoom;
        double unscaledY = e.getY() / currentZoom;

        hitTestCaret(unscaledX, unscaledY);
        hasSelection = true;
        selEndElem = currentElementIndex;
        selEndRun = currentRunIndex;
        selEndChar = currentCharOffset;
        caretVisible = true;
        render();
    }

    private void handleCanvasReleased(MouseEvent e) {
        if (selStartElem == selEndElem && selStartRun == selEndRun && selStartChar == selEndChar) {
            hasSelection = false;
        }
    }

    private void hitTestCaret(double mouseX, double mouseY) {
        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double pageX = (canvas.getWidth() / currentZoom - pageW) / 2.0;

        // Find clicked page
        int pageIdx = (int) ((mouseY - gap) / (pageH + gap));
        pageIdx = Math.max(0, Math.min(pages.size() - 1, pageIdx));
        LayoutPage page = pages.get(pageIdx);
        double pageTop = gap + pageIdx * (pageH + gap);

        // Find line closest to mouseY
        LayoutLine bestLine = null;
        double bestDist = Double.MAX_VALUE;

        for (LayoutLine line : page.getLines()) {
            double lineY = pageTop + line.getY();
            double dist = Math.abs(mouseY - (lineY + line.getHeight() / 2.0));
            if (dist < bestDist) {
                bestDist = dist;
                bestLine = line;
            }
        }

        if (bestLine != null) {
            Paragraph p = bestLine.getParagraph();
            currentElementIndex = document.getElements().indexOf(p);
            caretHeight = Math.max(12, bestLine.getHeight() * 0.85);
            caretY = pageTop + bestLine.getY() + (bestLine.getHeight() - caretHeight) / 2.0;

            // Find segment & char offset in bestLine
            double lineLeft = pageX + bestLine.getX();
            double curX = lineLeft;
            boolean placed = false;

            for (LayoutLine.LineSegment seg : bestLine.getSegments()) {
                double segLeft = pageX + bestLine.getX() + seg.getX();
                double segW = seg.getWidth();

                if (mouseX >= segLeft && mouseX <= segLeft + segW) {
                    currentRunIndex = p.getRuns().indexOf(seg.getRun());
                    String text = seg.getText();
                    double charW = text.isEmpty() ? 0 : segW / text.length();
                    int cIdx = (int) Math.round((mouseX - segLeft) / Math.max(1, charW));
                    cIdx = Math.max(0, Math.min(text.length(), cIdx));
                    currentCharOffset = cIdx;
                    caretX = segLeft + (cIdx * charW);
                    placed = true;
                    break;
                }
                curX = segLeft + segW;
            }

            if (!placed) {
                if (mouseX < lineLeft) {
                    caretX = lineLeft;
                    currentRunIndex = 0;
                    currentCharOffset = 0;
                } else {
                    caretX = curX;
                    currentRunIndex = Math.max(0, p.getRuns().size() - 1);
                    if (!p.getRuns().isEmpty()) {
                        currentCharOffset = p.getRuns().get(currentRunIndex).getText().length();
                    }
                }
            }
        } else {
            caretX = pageX + document.getPageSetup().getMarginLeft();
            caretY = pageTop + document.getPageSetup().getMarginTop();
            caretHeight = 14;
        }
    }

    private void handleKeyPressed(KeyEvent e) {
        Paragraph p = getActiveParagraph();
        if (p == null) return;

        if (e.getCode() == KeyCode.ENTER) {
            // Split paragraph
            Paragraph newP = new Paragraph();
            if (!p.getRuns().isEmpty() && currentRunIndex < p.getRuns().size()) {
                TextRun currentRun = p.getRuns().get(currentRunIndex);
                String txt = currentRun.getText();
                if (currentCharOffset < txt.length()) {
                    String tail = txt.substring(currentCharOffset);
                    currentRun.setText(txt.substring(0, currentCharOffset));
                    TextRun newRun = currentRun.copy();
                    newRun.setText(tail);
                    newP.addRun(newRun);
                }
            }

            int insertIdx = currentElementIndex + 1;
            if (insertIdx <= document.getElements().size()) {
                document.getElements().add(insertIdx, newP);
            } else {
                document.addElement(newP);
            }

            currentElementIndex = insertIdx;
            currentRunIndex = 0;
            currentCharOffset = 0;
            document.setModified(true);
            relayout();
            e.consume();

        } else if (e.getCode() == KeyCode.BACK_SPACE) {
            deleteCharacter(true);
            e.consume();

        } else if (e.getCode() == KeyCode.DELETE) {
            deleteCharacter(false);
            e.consume();

        } else if (e.getCode() == KeyCode.LEFT) {
            moveCaret(-1);
            e.consume();

        } else if (e.getCode() == KeyCode.RIGHT) {
            moveCaret(1);
            e.consume();

        } else if (e.getCode() == KeyCode.UP) {
            caretY -= 16;
            caretVisible = true;
            render();
            e.consume();

        } else if (e.getCode() == KeyCode.DOWN) {
            caretY += 16;
            caretVisible = true;
            render();
            e.consume();
        }
    }

    private void deleteCharacter(boolean backwards) {
        Paragraph p = getActiveParagraph();
        if (p == null || p.getRuns().isEmpty()) return;

        currentRunIndex = Math.min(currentRunIndex, p.getRuns().size() - 1);
        TextRun run = p.getRuns().get(currentRunIndex);
        String txt = run.getText();

        if (backwards) {
            if (currentCharOffset > 0 && currentCharOffset <= txt.length()) {
                run.setText(txt.substring(0, currentCharOffset - 1) + txt.substring(currentCharOffset));
                currentCharOffset--;
                document.setModified(true);
                relayout();
            } else if (currentRunIndex > 0) {
                currentRunIndex--;
                currentCharOffset = p.getRuns().get(currentRunIndex).getText().length();
            }
        } else {
            if (currentCharOffset >= 0 && currentCharOffset < txt.length()) {
                run.setText(txt.substring(0, currentCharOffset) + txt.substring(currentCharOffset + 1));
                document.setModified(true);
                relayout();
            }
        }
    }

    private void moveCaret(int delta) {
        Paragraph p = getActiveParagraph();
        if (p == null || p.getRuns().isEmpty()) return;

        TextRun run = p.getRuns().get(Math.min(currentRunIndex, p.getRuns().size() - 1));
        int nextChar = currentCharOffset + delta;

        if (nextChar >= 0 && nextChar <= run.getText().length()) {
            currentCharOffset = nextChar;
        } else if (delta < 0 && currentRunIndex > 0) {
            currentRunIndex--;
            currentCharOffset = p.getRuns().get(currentRunIndex).getText().length();
        } else if (delta > 0 && currentRunIndex < p.getRuns().size() - 1) {
            currentRunIndex++;
            currentCharOffset = 0;
        }
        caretVisible = true;
        render();
    }

    private void handleKeyTyped(KeyEvent e) {
        String ch = e.getCharacter();
        if (ch == null || ch.isEmpty() || ch.charAt(0) < 32) return;

        Paragraph p = getActiveParagraph();
        if (p == null) {
            p = new Paragraph();
            document.addElement(p);
            currentElementIndex = document.getElements().indexOf(p);
        }

        if (p.getRuns().isEmpty()) {
            TextRun r = new TextRun(ch);
            p.addRun(r);
            currentRunIndex = 0;
            currentCharOffset = 1;
        } else {
            currentRunIndex = Math.min(currentRunIndex, p.getRuns().size() - 1);
            TextRun run = p.getRuns().get(currentRunIndex);
            String txt = run.getText();
            currentCharOffset = Math.max(0, Math.min(currentCharOffset, txt.length()));
            run.setText(txt.substring(0, currentCharOffset) + ch + txt.substring(currentCharOffset));
            currentCharOffset++;
        }

        document.setModified(true);
        relayout();
    }

    private void updateStatus() {
        statusStatsLabel.setText(String.format("Pages: %d | Words: %d | Characters: %d",
                pages.size(), document.getWordCount(), document.getCharacterCount()));
    }

    // --- Search & Replace Implementation ---

    private void toggleSearch(boolean visible) {
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible) {
            setTop(new VBox(searchBar));
            searchField.requestFocus();
        } else {
            setTop(null);
            searchResults.clear();
            searchResultLabel.setText("");
        }
    }

    private void performSearch(String query, boolean jumpToFirst) {
        searchResults.clear();
        if (query == null || query.isBlank()) {
            searchResultLabel.setText("");
            return;
        }

        for (int eIdx = 0; eIdx < document.getElements().size(); eIdx++) {
            DocumentElement el = document.getElements().get(eIdx);
            if (el instanceof Paragraph p) {
                String full = p.getPlainText();
                int idx = 0;
                while ((idx = full.toLowerCase().indexOf(query.toLowerCase(), idx)) != -1) {
                    searchResults.add(new SearchResult(eIdx, idx, query.length()));
                    idx += query.length();
                }
            }
        }

        if (!searchResults.isEmpty()) {
            currentMatchIndex = 0;
            searchResultLabel.setText("Match 1 of " + searchResults.size());
            if (jumpToFirst) {
                jumpToMatch(searchResults.get(0));
            }
        } else {
            searchResultLabel.setText("No matches");
        }
    }

    private void navigateSearch(int direction) {
        if (searchResults.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + direction + searchResults.size()) % searchResults.size();
        searchResultLabel.setText("Match " + (currentMatchIndex + 1) + " of " + searchResults.size());
        jumpToMatch(searchResults.get(currentMatchIndex));
    }

    private void jumpToMatch(SearchResult match) {
        currentElementIndex = match.elemIndex;
        Paragraph p = (Paragraph) document.getElements().get(match.elemIndex);
        currentRunIndex = 0;
        currentCharOffset = match.charIndex;
        caretVisible = true;
        render();
    }

    private void performReplace(boolean all) {
        String find = searchField.getText();
        String replace = replaceField.getText();
        if (find == null || find.isEmpty()) return;

        if (all) {
            for (DocumentElement el : document.getElements()) {
                if (el instanceof Paragraph p) {
                    for (TextRun run : p.getRuns()) {
                        if (run.getText().contains(find)) {
                            run.setText(run.getText().replace(find, replace));
                        }
                    }
                }
            }
            document.setModified(true);
            relayout();
            performSearch(find, false);
        } else {
            if (!searchResults.isEmpty() && currentMatchIndex < searchResults.size()) {
                SearchResult sr = searchResults.get(currentMatchIndex);
                Paragraph p = (Paragraph) document.getElements().get(sr.elemIndex);
                if (!p.getRuns().isEmpty()) {
                    TextRun r = p.getRuns().get(0);
                    r.setText(r.getText().replaceFirst(PatternQuote(find), replace));
                    document.setModified(true);
                    relayout();
                    performSearch(find, true);
                }
            }
        }
    }

    private static String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    private static class SearchResult {
        final int elemIndex;
        final int charIndex;
        final int length;

        SearchResult(int elemIndex, int charIndex, int length) {
            this.elemIndex = elemIndex;
            this.charIndex = charIndex;
            this.length = length;
        }
    }

    // --- DocumentView Interface Implementations ---

    @Override
    public DocumentModel getDocument() {
        return document;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbon;
    }

    @Override
    public Node getStatusBarNode() {
        return statusStatsLabel;
    }

    @Override
    public double getZoom() {
        return currentZoom;
    }

    @Override
    public void setZoom(double zoom) {
        this.currentZoom = Math.max(0.25, Math.min(4.0, zoom));
        relayout();
    }

    @Override
    public void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(document.getPlainText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void cut() {
        copy();
        Paragraph p = getActiveParagraph();
        if (p != null) {
            p.getRuns().clear();
            document.setModified(true);
            relayout();
        }
    }

    @Override
    public void paste() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb.hasString()) {
            String text = cb.getString();
            Paragraph p = getActiveParagraph();
            if (p == null) {
                p = new Paragraph();
                document.addElement(p);
            }
            p.addRun(new TextRun(text));
            document.setModified(true);
            relayout();
        }
    }

    @Override
    public void selectAll() {
        hasSelection = true;
        selStartElem = 0;
        selStartRun = 0;
        selStartChar = 0;
        selEndElem = Math.max(0, document.getElements().size() - 1);
        caretVisible = true;
        render();
    }

    @Override
    public void showSearch() {
        toggleSearch(true);
    }

    @Override
    public void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene() != null ? getScene().getWindow() : null)) {
            Printer printer = job.getPrinter();
            PageLayout layout = printer.createPageLayout(
                    Paper.A4,
                    document.getPageSetup().getOrientation() == PageSetup.Orientation.LANDSCAPE ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT,
                    Printer.MarginType.DEFAULT
            );
            job.getJobSettings().setPageLayout(layout);

            boolean success = true;
            for (LayoutPage page : pages) {
                Canvas printCanvas = new Canvas(document.getPageSetup().getPageWidth(), document.getPageSetup().getPageHeight());
                GraphicsContext gc = printCanvas.getGraphicsContext2D();
                gc.setFill(Color.WHITE);
                gc.fillRect(0, 0, printCanvas.getWidth(), printCanvas.getHeight());

                if (page.getHeaderLine() != null) drawLine(gc, page.getHeaderLine(), 0, 0);
                if (page.getFooterLine() != null) drawLine(gc, page.getFooterLine(), 0, 0);
                for (LayoutLine line : page.getLines()) drawLine(gc, line, 0, 0);
                for (LayoutPage.TableBox tb : page.getTableBoxes()) drawTableBox(gc, tb, 0, 0);
                for (LayoutPage.ImageBox ib : page.getImageBoxes()) {
                    if (ib.image != null) gc.drawImage(ib.image, ib.x, ib.y, ib.width, ib.height);
                }

                if (!job.printPage(printCanvas)) {
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
        if (caretBlink != null) {
            caretBlink.stop();
        }
    }
}
