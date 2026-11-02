package org.originsd.oqteris.writer.ui;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.Duration;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.materialdesign2.*;
import org.originsd.oqteris.core.model.DocumentModel;
import org.originsd.oqteris.core.render.FontManager;
import org.originsd.oqteris.ui.DocumentView;
import org.originsd.oqteris.ui.icon.AppIcons;
import org.originsd.oqteris.writer.layout.LayoutEngine;
import org.originsd.oqteris.writer.layout.LayoutLine;
import org.originsd.oqteris.writer.layout.LayoutPage;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main UI view and interactive multi-page canvas for Oqteris Writer.
 */
public class WriterDocumentView extends BorderPane implements DocumentView {
    /**
     * Prism/GPU textures usually fail above ~8192px on a side.
     * Long lab reports must not expand a single Canvas past this limit.
     */
    private static final double MAX_CANVAS_DIMENSION = 8192.0;
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
    private final RulerBar rulerBar = new RulerBar(this);
    // Search and Replace Bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = TextFields.createClearableTextField();
    private final TextField replaceField = TextFields.createClearableTextField();
    private final Label searchResultLabel = new Label("");
    private final List<SearchResult> searchResults = new ArrayList<>();
    private int currentMatchIndex = 0;

    // Formatting Toolbar controls
    private final ComboBox<String> fontCombo = new ComboBox<>();
    private final ComboBox<Double> sizeCombo = new ComboBox<>();
    private final ToggleButton boldBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_BOLD, "Bold (Ctrl+B)");
    private final ToggleButton italicBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_ITALIC, "Italic (Ctrl+I)");
    private final ToggleButton underlineBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_UNDERLINE, "Underline (Ctrl+U)");
    private final ToggleButton strikeBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_STRIKETHROUGH, "Strikethrough");
    private final ColorPicker textColor = new ColorPicker(Color.BLACK);
    private final ColorPicker highlightColor = new ColorPicker(Color.TRANSPARENT);
    private final ToggleButton alignLeft = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_LEFT, "Align Left");
    private final ToggleButton alignCenter = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_CENTER, "Center");
    private final ToggleButton alignRight = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_RIGHT, "Align Right");
    private final ToggleButton alignJustify = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_JUSTIFY, "Justify");
    private final ToggleButton bulletBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_LIST_BULLETED, "Bulleted List");
    private final ToggleButton numberBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_LIST_NUMBERED, "Numbered List");
    private final ColorPicker cellShadingPicker = new ColorPicker(Color.WHITE);
    private final ColorPicker tableBorderColorPicker = new ColorPicker(Color.rgb(200, 200, 200));
    private final ComboBox<Double> borderWidthCombo = new ComboBox<>();
    private final ComboBox<Double> lineSpacingCombo = new ComboBox<>();
    private Paragraph activeParagraph = null;
    private DocumentTable activeTable = null;
    private DocumentTableCell activeCell = null;
    private int activeRowIndex = -1;
    private int activeColIndex = -1;
    private boolean updatingToolbar = false;

    public WriterDocumentView() {
        this(new WriterDocument());
    }

    public WriterDocumentView(WriterDocument document) {
        this.document = document;
        buildUI();
        updateToolbarFromDocument();
        relayout();
        setupCaretBlink();
    }

    private static Spinner<Double> inchSpinner(double initial) {
        SpinnerValueFactory.DoubleSpinnerValueFactory factory =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 4.0, initial, 0.05);
        factory.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Double v) {
                return v == null ? "0.00" : String.format("%.2f", v);
            }

            @Override
            public Double fromString(String s) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (Exception e) {
                    return 0.0;
                }
            }
        });
        Spinner<Double> sp = new Spinner<>(factory);
        sp.setEditable(true);
        sp.setPrefWidth(90);
        return sp;
    }

    private static String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
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
        canvasContainer.setOnMousePressed(e -> canvas.requestFocus());
        scrollPane.setOnMousePressed(e -> canvas.requestFocus());

        VBox centerContainer = new VBox();
        centerContainer.getChildren().addAll(rulerBar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        setCenter(centerContainer);

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

        // All installed system fonts (Word-like font picker)
        fontCombo.getItems().clear();
        fontCombo.getItems().addAll(FontManager.getInstance().getAvailableFontFamilies());
        String detected = document != null ? document.getDetectedFontFamily() : "Calibri";
        if (fontCombo.getItems().contains(detected)) {
            fontCombo.setValue(detected);
        } else if (!fontCombo.getItems().isEmpty()) {
            fontCombo.setValue(fontCombo.getItems().get(0));
        }
        fontCombo.setPrefWidth(160);
        fontCombo.setEditable(true);
        fontCombo.setOnAction(e -> {
            if (updatingToolbar) return;
            String fam = fontCombo.getValue();
            if (fam != null && !fam.isBlank()) applyRunStyle(r -> r.setFontFamily(fam));
        });

        sizeCombo.getItems().clear();
        sizeCombo.getItems().addAll(8.0, 9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0, 48.0, 72.0);
        sizeCombo.setValue(document != null ? document.getDefaultFontSize() : 11.0);
        sizeCombo.setPrefWidth(70);
        sizeCombo.setEditable(true);
        sizeCombo.setOnAction(e -> {
            if (updatingToolbar) return;
            Double sz = sizeCombo.getValue();
            if (sz != null && sz > 0) applyRunStyle(r -> r.setFontSize(sz));
        });
        sizeCombo.getEditor().setOnAction(e -> {
            if (updatingToolbar) return;
            try {
                double v = Double.parseDouble(sizeCombo.getEditor().getText().trim());
                if (v > 0) {
                    sizeCombo.setValue(v);
                    applyRunStyle(r -> r.setFontSize(v));
                }
            } catch (Exception ignored) {}
        });

        Button growFontBtn = AppIcons.button("A+", MaterialDesignF.FORMAT_FONT_SIZE_INCREASE, e -> changeFontSize(2.0));
        Button shrinkFontBtn = AppIcons.button("A−", MaterialDesignF.FORMAT_FONT_SIZE_DECREASE, e -> changeFontSize(-2.0));
        Button clearFmtBtn = AppIcons.button("Clear", MaterialDesignF.FORMAT_CLEAR, e -> clearSelectionFormatting());

        boldBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setBold(boldBtn.isSelected()));
        });

        italicBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setItalic(italicBtn.isSelected()));
        });

        underlineBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setUnderline(underlineBtn.isSelected()));
        });

        strikeBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setStrikethrough(strikeBtn.isSelected()));
        });

        textColor.setPrefWidth(46);
        textColor.setTooltip(new Tooltip("Text Color"));
        textColor.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setTextColor(textColor.getValue()));
        });

        highlightColor.setPrefWidth(46);
        highlightColor.setTooltip(new Tooltip("Highlight Color"));
        highlightColor.setOnAction(e -> {
            if (updatingToolbar) return;
            applyRunStyle(r -> r.setHighlightColor(highlightColor.getValue()));
        });

        fontGroup.getNodes().addAll(fontCombo, sizeCombo, growFontBtn, shrinkFontBtn, boldBtn, italicBtn, underlineBtn, strikeBtn, textColor, highlightColor, clearFmtBtn);

        // Group: Paragraph
        RibbonGroup paragraphGroup = new RibbonGroup();
        paragraphGroup.setTitle("Paragraph");

        alignLeft.setOnAction(e -> {
            if (updatingToolbar) return;
            setParagraphAlignment(Paragraph.Alignment.LEFT);
        });

        alignCenter.setOnAction(e -> {
            if (updatingToolbar) return;
            setParagraphAlignment(Paragraph.Alignment.CENTER);
        });

        alignRight.setOnAction(e -> {
            if (updatingToolbar) return;
            setParagraphAlignment(Paragraph.Alignment.RIGHT);
        });

        alignJustify.setOnAction(e -> {
            if (updatingToolbar) return;
            setParagraphAlignment(Paragraph.Alignment.JUSTIFY);
        });

        SegmentedButton alignSegmented = new SegmentedButton(alignLeft, alignCenter, alignRight, alignJustify);

        lineSpacingCombo.getItems().clear();
        lineSpacingCombo.getItems().addAll(1.0, 1.15, 1.5, 2.0, 2.5, 3.0);
        lineSpacingCombo.setValue(1.0);
        lineSpacingCombo.setPrefWidth(65);
        lineSpacingCombo.setTooltip(new Tooltip("Line Spacing"));
        lineSpacingCombo.setOnAction(e -> {
            if (updatingToolbar) return;
            Double ls = lineSpacingCombo.getValue();
            if (ls != null && ls > 0) {
                Paragraph p = getActiveParagraph();
                if (p != null) {
                    p.setLineSpacing(ls);
                    document.setModified(true);
                    relayout();
                }
            }
        });

        bulletBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.BULLET ? Paragraph.ListType.NONE : Paragraph.ListType.BULLET);
                document.setModified(true);
                relayout();
            }
        });

        numberBtn.setOnAction(e -> {
            if (updatingToolbar) return;
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.NUMBERED ? Paragraph.ListType.NONE : Paragraph.ListType.NUMBERED);
                document.setModified(true);
                relayout();
            }
        });

        Button indentIncBtn = AppIcons.button("+Indent", MaterialDesignF.FORMAT_INDENT_INCREASE, e -> adjustIndent(18.0));
        Button indentDecBtn = AppIcons.button("−Indent", MaterialDesignF.FORMAT_INDENT_DECREASE, e -> adjustIndent(-18.0));

        paragraphGroup.getNodes().addAll(alignSegmented, lineSpacingCombo, bulletBtn, numberBtn, indentDecBtn, indentIncBtn);

        // Group: Styles
        RibbonGroup styleGroup = new RibbonGroup();
        styleGroup.setTitle("Styles");

        ComboBox<Paragraph.HeadingStyle> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(Paragraph.HeadingStyle.values());
        styleCombo.setValue(Paragraph.HeadingStyle.NORMAL);
        styleCombo.setPrefWidth(125);
        styleCombo.setOnAction(e -> {
            if (updatingToolbar) return;
            Paragraph.HeadingStyle style = styleCombo.getValue();
            if (style == null) return;
            if (hasSelection) {
                int startE = Math.min(selStartElem, selEndElem);
                int endE = Math.max(selStartElem, selEndElem);
                if (startE >= 0 && endE >= 0) {
                    for (int elIdx = startE; elIdx <= endE && elIdx < document.getElements().size(); elIdx++) {
                        DocumentElement el = document.getElements().get(elIdx);
                        if (el instanceof Paragraph p) {
                            p.setHeadingStyle(style);
                            applyHeadingStyleToRuns(p, style);
                        }
                    }
                }
            }
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setHeadingStyle(style);
                applyHeadingStyleToRuns(p, style);
            }
            document.setModified(true);
            relayout();
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
            int idx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();
            document.getElements().add(idx, table);
            Paragraph afterP = new Paragraph();
            document.getElements().add(idx + 1, afterP);
            currentElementIndex = idx + 1;
            activeParagraph = afterP;
            activeTable = table;
            document.setModified(true);
            relayout();
        });
        Button customTableBtn = AppIcons.button("Custom Table…", MaterialDesignT.TABLE_PLUS, e -> showInsertTableDialog());
        Button addRowAboveBtn = AppIcons.button("+Row Above", MaterialDesignT.TABLE_ROW_PLUS_BEFORE, e -> insertTableRow(false));
        Button addRowBelowBtn = AppIcons.button("+Row Below", MaterialDesignT.TABLE_ROW_PLUS_AFTER, e -> insertTableRow(true));
        Button addColLeftBtn = AppIcons.button("+Col Left", MaterialDesignT.TABLE_COLUMN_PLUS_BEFORE, e -> insertTableColumn(false));
        Button addColRightBtn = AppIcons.button("+Col Right", MaterialDesignT.TABLE_COLUMN_PLUS_AFTER, e -> insertTableColumn(true));
        Button delRowBtn = AppIcons.button("−Row", MaterialDesignT.TABLE_ROW_REMOVE, e -> deleteTableRow());
        Button delColBtn = AppIcons.button("−Col", MaterialDesignT.TABLE_COLUMN_REMOVE, e -> deleteTableColumn());
        Button delTableBtn = AppIcons.button("Delete Table", MaterialDesignT.TABLE_REMOVE, e -> deleteActiveTable());
        tableGroup.getNodes().addAll(insertTableBtn, customTableBtn, addRowAboveBtn, addRowBelowBtn, addColLeftBtn, addColRightBtn, delRowBtn, delColBtn, delTableBtn);

        // Group: Table Styling
        RibbonGroup tableDesignGroup = new RibbonGroup();
        tableDesignGroup.setTitle("Table Styling");
        cellShadingPicker.setPrefWidth(46);
        cellShadingPicker.setTooltip(new Tooltip("Cell Shading / Background"));
        cellShadingPicker.setOnAction(e -> applyCellShading(cellShadingPicker.getValue()));

        tableBorderColorPicker.setPrefWidth(46);
        tableBorderColorPicker.setTooltip(new Tooltip("Table Border Color"));
        tableBorderColorPicker.setOnAction(e -> applyTableBorderColor(tableBorderColorPicker.getValue()));

        borderWidthCombo.getItems().clear();
        borderWidthCombo.getItems().addAll(0.0, 0.5, 1.0, 1.5, 2.25, 3.0, 4.5, 6.0);
        borderWidthCombo.setValue(1.0);
        borderWidthCombo.setPrefWidth(65);
        borderWidthCombo.setTooltip(new Tooltip("Border Width (pt)"));
        borderWidthCombo.setOnAction(e -> {
            Double bw = borderWidthCombo.getValue();
            if (bw != null) applyTableBorderWidth(bw);
        });

        ToggleButton tblAlignLeft = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_LEFT, "Table Align Left");
        tblAlignLeft.setOnAction(e -> applyTableAlignment(DocumentTable.Alignment.LEFT));
        ToggleButton tblAlignCenter = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_CENTER, "Table Align Center");
        tblAlignCenter.setOnAction(e -> applyTableAlignment(DocumentTable.Alignment.CENTER));
        ToggleButton tblAlignRight = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_RIGHT, "Table Align Right");
        tblAlignRight.setOnAction(e -> applyTableAlignment(DocumentTable.Alignment.RIGHT));
        SegmentedButton tblAlignSeg = new SegmentedButton(tblAlignLeft, tblAlignCenter, tblAlignRight);

        ToggleButton cellVTop = AppIcons.toggleButton(MaterialDesignF.FORMAT_VERTICAL_ALIGN_TOP, "Cell Top Align");
        cellVTop.setOnAction(e -> applyCellVerticalAlignment(DocumentTableCell.VAlign.TOP));
        ToggleButton cellVCenter = AppIcons.toggleButton(MaterialDesignF.FORMAT_VERTICAL_ALIGN_CENTER, "Cell Center Align");
        cellVCenter.setOnAction(e -> applyCellVerticalAlignment(DocumentTableCell.VAlign.CENTER));
        ToggleButton cellVBottom = AppIcons.toggleButton(MaterialDesignF.FORMAT_VERTICAL_ALIGN_BOTTOM, "Cell Bottom Align");
        cellVBottom.setOnAction(e -> applyCellVerticalAlignment(DocumentTableCell.VAlign.BOTTOM));
        SegmentedButton cellVSeg = new SegmentedButton(cellVTop, cellVCenter, cellVBottom);

        tableDesignGroup.getNodes().addAll(cellShadingPicker, tableBorderColorPicker, borderWidthCombo, tblAlignSeg, cellVSeg);

        RibbonGroup mediaGroup = new RibbonGroup();
        mediaGroup.setTitle("Illustrations");
        Button insertImageBtn = AppIcons.button("Pictures", MaterialDesignI.IMAGE, e -> handleInsertImage());
        Button rotateImgBtn = AppIcons.button("Rotate", MaterialDesignR.ROTATE_RIGHT, e -> rotateSelectedImage(90));
        Button cropImgBtn = AppIcons.button("Crop", MaterialDesignC.CROP, e -> cropSelectedImage());
        Button brightenBtn = AppIcons.button("Bright+", MaterialDesignB.BRIGHTNESS_6, e -> adjustImageBrightness(0.15));
        Button darkenBtn = AppIcons.button("Bright−", MaterialDesignB.BRIGHTNESS_4, e -> adjustImageBrightness(-0.15));
        mediaGroup.getNodes().addAll(insertImageBtn, rotateImgBtn, cropImgBtn, brightenBtn, darkenBtn);

        RibbonGroup linksGroup = new RibbonGroup();
        linksGroup.setTitle("Links");
        Button insertLinkBtn = AppIcons.button("Hyperlink", MaterialDesignL.LINK, e -> handleInsertLink());
        linksGroup.getNodes().add(insertLinkBtn);

        RibbonGroup pagesGroup = new RibbonGroup();
        pagesGroup.setTitle("Pages");
        Button insertPageBreakBtn = AppIcons.button("Page Break", MaterialDesignP.PAGE_NEXT, e -> insertPageBreak());
        Button insertBlankPageBtn = AppIcons.button("Blank Page", MaterialDesignP.PAGE_LAYOUT_BODY, e -> insertBlankPage());
        pagesGroup.getNodes().addAll(insertPageBreakBtn, insertBlankPageBtn);

        RibbonGroup symbolsGroup = new RibbonGroup();
        symbolsGroup.setTitle("Rules & Text");
        Button horizRuleBtn = AppIcons.button("Horizontal Line", MaterialDesignM.MINUS, e -> insertHorizontalRule());
        Button dateTimeBtn = AppIcons.button("Date & Time", MaterialDesignC.CALENDAR_CLOCK, e -> insertDateTime());
        symbolsGroup.getNodes().addAll(horizRuleBtn, dateTimeBtn);

        insertTab.getRibbonGroups().addAll(tableGroup, tableDesignGroup, mediaGroup, linksGroup, pagesGroup, symbolsGroup);

        // =========================================================================
        // Tab 3: LAYOUT
        // =========================================================================
        RibbonTab layoutTab = new RibbonTab("Layout");

        RibbonGroup pageSetupGroup = new RibbonGroup();
        pageSetupGroup.setTitle("Page Setup");

        ComboBox<String> orientCombo = new ComboBox<>();
        orientCombo.getItems().addAll("Portrait", "Landscape");
        orientCombo.setValue(document.getPageSetup().getOrientation() == PageSetup.Orientation.LANDSCAPE ? "Landscape" : "Portrait");
        orientCombo.setPrefWidth(100);
        orientCombo.setOnAction(e -> {
            document.getPageSetup().setOrientation("Landscape".equals(orientCombo.getValue()) ? PageSetup.Orientation.LANDSCAPE : PageSetup.Orientation.PORTRAIT);
            document.setModified(true);
            relayout();
        });

        ComboBox<PageSetup.PaperSize> paperCombo = new ComboBox<>();
        paperCombo.getItems().addAll(
                PageSetup.PaperSize.A4, PageSetup.PaperSize.A3, PageSetup.PaperSize.A5, PageSetup.PaperSize.A6,
                PageSetup.PaperSize.LETTER, PageSetup.PaperSize.LEGAL, PageSetup.PaperSize.TABLOID,
                PageSetup.PaperSize.EXECUTIVE, PageSetup.PaperSize.STATEMENT
        );
        paperCombo.setValue(document.getPageSetup().getPaperSize());
        paperCombo.setPrefWidth(165);
        paperCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(PageSetup.PaperSize s) {
                return s == null ? "" : s.displayName;
            }

            @Override
            public PageSetup.PaperSize fromString(String s) {
                return PageSetup.PaperSize.A4;
            }
        });
        paperCombo.setOnAction(e -> {
            PageSetup.PaperSize sel = paperCombo.getValue();
            if (sel != null) {
                document.getPageSetup().setPaperSize(sel);
                document.setModified(true);
                relayout();
            }
        });

        Button pageSetupBtn = AppIcons.button("Margins…", MaterialDesignC.COG, e -> showPageSetupDialog());
        pageSetupGroup.getNodes().addAll(orientCombo, paperCombo, pageSetupBtn);

        RibbonGroup paragraphSpacingGroup = new RibbonGroup();
        paragraphSpacingGroup.setTitle("Paragraph Spacing");

        Spinner<Double> beforeSp = new Spinner<>(0.0, 72.0, 0.0, 2.0);
        beforeSp.setPrefWidth(75);
        beforeSp.setEditable(true);
        beforeSp.setTooltip(new Tooltip("Space Before (pt)"));
        beforeSp.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingToolbar || newV == null) return;
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setSpaceBefore(newV);
                document.setModified(true);
                relayout();
            }
        });

        Spinner<Double> afterSp = new Spinner<>(0.0, 72.0, 6.0, 2.0);
        afterSp.setPrefWidth(75);
        afterSp.setEditable(true);
        afterSp.setTooltip(new Tooltip("Space After (pt)"));
        afterSp.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingToolbar || newV == null) return;
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setSpaceAfter(newV);
                document.setModified(true);
                relayout();
            }
        });

        paragraphSpacingGroup.getNodes().addAll(new Label("Before:"), beforeSp, new Label("After:"), afterSp);

        layoutTab.getRibbonGroups().addAll(pageSetupGroup, paragraphSpacingGroup);

        // =========================================================================
        // Tab 4: REVIEW & VIEW
        // =========================================================================
        RibbonTab viewTab = new RibbonTab("Review & View");

        RibbonGroup searchGroup = new RibbonGroup();
        searchGroup.setTitle("Editing");
        Button findBtn = AppIcons.button("Find & Replace", MaterialDesignF.FIND_REPLACE, e -> toggleSearch(!searchBar.isVisible()));
        searchGroup.getNodes().add(findBtn);

        RibbonGroup showGroup = new RibbonGroup();
        showGroup.setTitle("Show");
        ToggleButton rulerToggle = AppIcons.toggleButton(MaterialDesignR.RULER, "Ruler");
        rulerToggle.setSelected(true);
        rulerToggle.setOnAction(e -> {
            boolean show = rulerToggle.isSelected();
            rulerBar.setVisible(show);
            rulerBar.setManaged(show);
        });
        showGroup.getNodes().add(rulerToggle);

        RibbonGroup statsGroup = new RibbonGroup();
        statsGroup.setTitle("Statistics");
        Label wordBadge = AppIcons.badge("Words: " + document.getWordCount(), MaterialDesignT.TEXT_BOX_OUTLINE, "stats-badge");
        document.addDocumentListener(d -> Platform.runLater(() -> wordBadge.setText("Words: " + document.getWordCount())));
        statsGroup.getNodes().add(wordBadge);

        viewTab.getRibbonGroups().addAll(searchGroup, showGroup, statsGroup);

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
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                double maxW = document.getPageSetup().getPrintableWidth();
                double w = Math.min(img.getWidth(), maxW);
                double h = img.getHeight() * (w / Math.max(1, img.getWidth()));
                DocumentImage docImg = new DocumentImage(img, w, h);
                docImg.setRawBytes(bytes);

                int insertIdx = Math.min(currentElementIndex + 1, document.getElements().size());
                document.getElements().add(insertIdx, docImg);
                document.getElements().add(insertIdx + 1, new Paragraph());
                currentElementIndex = insertIdx + 1;
                currentRunIndex = 0;
                currentCharOffset = 0;
                document.setModified(true);
                relayout();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to load image: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        }
    }

    /**
     * Find the nearest DocumentImage to the caret (prefer previous element).
     */
    private DocumentImage findNearbyImage() {
        int idx = currentElementIndex;
        if (idx >= 0 && idx < document.getElements().size()) {
            DocumentElement el = document.getElements().get(idx);
            if (el instanceof DocumentImage img) return img;
        }
        for (int i = Math.min(idx, document.getElements().size() - 1); i >= 0; i--) {
            if (document.getElements().get(i) instanceof DocumentImage img) return img;
        }
        for (DocumentElement el : document.getElements()) {
            if (el instanceof DocumentImage img) return img;
        }
        return null;
    }

    private void rotateSelectedImage(int degrees) {
        DocumentImage img = findNearbyImage();
        if (img == null) {
            new Alert(Alert.AlertType.INFORMATION, "Place the caret near an image first.", ButtonType.OK).showAndWait();
            return;
        }
        img.rotateClockwise90();
        // Swap displayed width/height when rotating 90°
        if (degrees % 180 != 0) {
            double t = img.getWidth();
            img.setWidth(img.getHeight());
            img.setHeight(t);
        }
        document.setModified(true);
        relayout();
    }

    private void cropSelectedImage() {
        DocumentImage img = findNearbyImage();
        if (img == null || img.getImage() == null) {
            new Alert(Alert.AlertType.INFORMATION, "Place the caret near an image first.", ButtonType.OK).showAndWait();
            return;
        }
        // Simple 10% crop from each side (Office-style quick crop)
        double L = img.getCropLeft() + 0.08;
        double T = img.getCropTop() + 0.08;
        double R = img.getCropRight() + 0.08;
        double B = img.getCropBottom() + 0.08;
        if (L + R >= 0.9 || T + B >= 0.9) {
            img.clearCrop();
        } else {
            img.setCrop(L, T, R, B);
            // shrink layout size proportionally
            img.setWidth(img.getWidth() * (1 - 0.16));
            img.setHeight(img.getHeight() * (1 - 0.16));
        }
        document.setModified(true);
        relayout();
    }

    private void adjustImageBrightness(double delta) {
        DocumentImage img = findNearbyImage();
        if (img == null) {
            new Alert(Alert.AlertType.INFORMATION, "Place the caret near an image first.", ButtonType.OK).showAndWait();
            return;
        }
        img.setBrightness(img.getBrightness() + delta);
        document.setModified(true);
        relayout();
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
        dialog.setTitle("Page Setup – Oqteris Writer");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());

        PageSetup ps = document.getPageSetup();

        GridPane gp = new GridPane();
        gp.setHgap(12);
        gp.setVgap(10);
        gp.setPadding(new Insets(20));

        ComboBox<PageSetup.PaperSize> paperCombo = new ComboBox<>();
        paperCombo.getItems().addAll(
                PageSetup.PaperSize.A4, PageSetup.PaperSize.A3, PageSetup.PaperSize.A5, PageSetup.PaperSize.A6,
                PageSetup.PaperSize.LETTER, PageSetup.PaperSize.LEGAL, PageSetup.PaperSize.TABLOID,
                PageSetup.PaperSize.EXECUTIVE, PageSetup.PaperSize.STATEMENT, PageSetup.PaperSize.CUSTOM
        );
        paperCombo.setValue(ps.getPaperSize());
        paperCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(PageSetup.PaperSize s) {
                return s == null ? "" : s.displayName;
            }

            @Override
            public PageSetup.PaperSize fromString(String s) {
                return PageSetup.PaperSize.A4;
            }
        });

        ComboBox<String> orientCombo = new ComboBox<>();
        orientCombo.getItems().addAll("Portrait", "Landscape");
        orientCombo.setValue(ps.getOrientation() == PageSetup.Orientation.LANDSCAPE ? "Landscape" : "Portrait");

        // Exact margin spinners in inches (document values are in points)
        Spinner<Double> topSp = inchSpinner(ps.getMarginTop() / 72.0);
        Spinner<Double> bottomSp = inchSpinner(ps.getMarginBottom() / 72.0);
        Spinner<Double> leftSp = inchSpinner(ps.getMarginLeft() / 72.0);
        Spinner<Double> rightSp = inchSpinner(ps.getMarginRight() / 72.0);

        ComboBox<String> presetMargins = new ComboBox<>();
        presetMargins.getItems().addAll("Normal (1\")", "Narrow (0.5\")", "Moderate (0.75\")", "Wide (1.5\")", "Custom");
        presetMargins.setValue("Custom");
        presetMargins.setOnAction(ev -> {
            String v = presetMargins.getValue();
            double m = switch (v != null ? v : "") {
                case "Normal (1\")" -> 1.0;
                case "Narrow (0.5\")" -> 0.5;
                case "Moderate (0.75\")" -> 0.75;
                case "Wide (1.5\")" -> 1.5;
                default -> -1;
            };
            if (m > 0) {
                topSp.getValueFactory().setValue(m);
                bottomSp.getValueFactory().setValue(m);
                leftSp.getValueFactory().setValue(m);
                rightSp.getValueFactory().setValue(m);
            }
        });

        gp.addRow(0, new Label("Paper Size:"), paperCombo);
        gp.addRow(1, new Label("Orientation:"), orientCombo);
        gp.addRow(2, new Label("Margin preset:"), presetMargins);
        gp.addRow(3, new Label("Top (in):"), topSp);
        gp.addRow(4, new Label("Bottom (in):"), bottomSp);
        gp.addRow(5, new Label("Left (in):"), leftSp);
        gp.addRow(6, new Label("Right (in):"), rightSp);

        dialog.getDialogPane().setContent(gp);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(380);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            ps.setOrientation("Landscape".equals(orientCombo.getValue())
                    ? PageSetup.Orientation.LANDSCAPE : PageSetup.Orientation.PORTRAIT);

            PageSetup.PaperSize sel = paperCombo.getValue();
            if (sel != null && sel != PageSetup.PaperSize.CUSTOM) {
                ps.setPaperSize(sel);
            }

            ps.setMargins(
                    topSp.getValue() * 72.0,
                    rightSp.getValue() * 72.0,
                    bottomSp.getValue() * 72.0,
                    leftSp.getValue() * 72.0
            );

            document.setModified(true);
            relayout();
        }
    }

    public void updateToolbarFromDocument() {
        if (document == null) return;
        String appliedFont = document.getDetectedFontFamily();
        updatingToolbar = true;
        try {
            if (appliedFont != null && !appliedFont.isBlank()) {
                if (!fontCombo.getItems().contains(appliedFont)) {
                    fontCombo.getItems().add(0, appliedFont);
                }
                fontCombo.setValue(appliedFont);
            }
            double sz = document.getDefaultFontSize();
            if (sz > 0) {
                if (!sizeCombo.getItems().contains(sz)) {
                    sizeCombo.getItems().add(sz);
                }
                sizeCombo.setValue(sz);
            }
        } finally {
            updatingToolbar = false;
        }
    }

    public void updateToolbarFromSelection() {
        Paragraph p = getActiveParagraph();
        if (p == null) return;

        updatingToolbar = true;
        try {
            alignLeft.setSelected(p.getAlignment() == Paragraph.Alignment.LEFT);
            alignCenter.setSelected(p.getAlignment() == Paragraph.Alignment.CENTER);
            alignRight.setSelected(p.getAlignment() == Paragraph.Alignment.RIGHT);
            alignJustify.setSelected(p.getAlignment() == Paragraph.Alignment.JUSTIFY);

            bulletBtn.setSelected(p.getListType() == Paragraph.ListType.BULLET);
            numberBtn.setSelected(p.getListType() == Paragraph.ListType.NUMBERED);

            if (!p.getRuns().isEmpty()) {
                int rIdx = Math.min(Math.max(0, currentRunIndex), p.getRuns().size() - 1);
                TextRun run = p.getRuns().get(rIdx);

                if (run.getFontFamily() != null && !run.getFontFamily().isBlank()) {
                    if (!fontCombo.getItems().contains(run.getFontFamily())) {
                        fontCombo.getItems().add(0, run.getFontFamily());
                    }
                    fontCombo.setValue(run.getFontFamily());
                }
                if (run.getFontSize() > 0) {
                    if (!sizeCombo.getItems().contains(run.getFontSize())) {
                        sizeCombo.getItems().add(run.getFontSize());
                    }
                    sizeCombo.setValue(run.getFontSize());
                }

                boldBtn.setSelected(run.isBold());
                italicBtn.setSelected(run.isItalic());
                underlineBtn.setSelected(run.isUnderline());
                strikeBtn.setSelected(run.isStrikethrough());

                if (run.getTextColor() != null) {
                    textColor.setValue(run.getTextColor());
                }
                if (run.getHighlightColor() != null) {
                    highlightColor.setValue(run.getHighlightColor());
                }
            }
        } finally {
            updatingToolbar = false;
        }
    }

    private void applyRunStyle(java.util.function.Consumer<TextRun> modifier) {
        if (hasSelection) {
            applyStyleToSelection(modifier);
            document.setModified(true);
            relayout();
            return;
        }

        Paragraph p = getActiveParagraph();
        if (p != null) {
            if (!p.getRuns().isEmpty()) {
                TextRun r = p.getRuns().get(Math.min(currentRunIndex, p.getRuns().size() - 1));
                modifier.accept(r);
            } else {
                TextRun r = new TextRun("");
                modifier.accept(r);
                p.addRun(r);
            }
            document.setModified(true);
            relayout();
        }
    }

    private void applyStyleToSelection(java.util.function.Consumer<TextRun> modifier) {
        if (selStartElem == selEndElem && selStartElem >= 0 && selStartElem < document.getElements().size()) {
            DocumentElement el = document.getElements().get(selStartElem);
            if (el instanceof Paragraph p && !p.getRuns().isEmpty()) {
                int sRun = Math.min(selStartRun, selEndRun);
                int eRun = Math.max(selStartRun, selEndRun);
                sRun = Math.max(0, Math.min(p.getRuns().size() - 1, sRun));
                eRun = Math.max(0, Math.min(p.getRuns().size() - 1, eRun));

                int sChar = (selStartRun <= selEndRun) ? selStartChar : selEndChar;
                int eChar = (selStartRun <= selEndRun) ? selEndChar : selStartChar;

                if (sRun == eRun) {
                    TextRun r = p.getRuns().get(sRun);
                    String txt = r.getText();
                    int c1 = Math.min(sChar, eChar);
                    int c2 = Math.max(sChar, eChar);
                    c1 = Math.max(0, Math.min(txt.length(), c1));
                    c2 = Math.max(0, Math.min(txt.length(), c2));

                    if (c1 < c2) {
                        String prefix = txt.substring(0, c1);
                        String middle = txt.substring(c1, c2);
                        String suffix = txt.substring(c2);

                        r.setText(prefix);
                        TextRun midRun = r.copy();
                        midRun.setText(middle);
                        modifier.accept(midRun);

                        int insertAt = sRun + 1;
                        if (!middle.isEmpty()) {
                            p.getRuns().add(insertAt++, midRun);
                        }
                        if (!suffix.isEmpty()) {
                            TextRun sufRun = r.copy();
                            sufRun.setText(suffix);
                            p.getRuns().add(insertAt, sufRun);
                        }
                        if (prefix.isEmpty()) {
                            p.getRuns().remove(sRun);
                        }
                        return;
                    }
                }
            }
        }

        int startE = Math.min(selStartElem, selEndElem);
        int endE = Math.max(selStartElem, selEndElem);
        for (int e = Math.max(0, startE); e <= endE && e < document.getElements().size(); e++) {
            DocumentElement el = document.getElements().get(e);
            if (el instanceof Paragraph p) {
                for (TextRun r : p.getRuns()) {
                    modifier.accept(r);
                }
            }
        }
    }

    private void applyHeadingStyleToRuns(Paragraph p, Paragraph.HeadingStyle style) {
        if (p == null) return;
        double sz = switch (style) {
            case TITLE -> 26.0;
            case HEADING_1 -> 20.0;
            case HEADING_2 -> 16.0;
            case HEADING_3 -> 14.0;
            default -> document != null ? document.getDefaultFontSize() : 11.0;
        };
        boolean bold = (style != Paragraph.HeadingStyle.NORMAL);
        if (p.getRuns().isEmpty()) {
            TextRun r = new TextRun("");
            r.setFontSize(sz);
            r.setBold(bold);
            p.addRun(r);
        } else {
            for (TextRun r : p.getRuns()) {
                r.setFontSize(sz);
                r.setBold(bold);
            }
        }
    }

    private void setParagraphAlignment(Paragraph.Alignment align) {
        if (hasSelection) {
            int startE = Math.min(selStartElem, selEndElem);
            int endE = Math.max(selStartElem, selEndElem);
            if (startE >= 0 && endE >= 0) {
                for (int e = startE; e <= endE && e < document.getElements().size(); e++) {
                    DocumentElement el = document.getElements().get(e);
                    if (el instanceof Paragraph p) {
                        p.setAlignment(align);
                    }
                }
            }
        }
        Paragraph p = getActiveParagraph();
        if (p != null) {
            p.setAlignment(align);
        }
        document.setModified(true);
        relayout();
    }

    public Paragraph getActiveParagraph() {
        if (activeParagraph != null) return activeParagraph;
        if (currentElementIndex >= 0 && currentElementIndex < document.getElements().size()) {
            DocumentElement el = document.getElements().get(currentElementIndex);
            if (el instanceof Paragraph p) return p;
        }
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) return p;
        }
        return null;
    }

    public void insertPageBreak() {
        Paragraph p = getActiveParagraph();
        int insertIdx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();

        Paragraph newP = new Paragraph();
        if (p != null && !p.getRuns().isEmpty() && currentRunIndex < p.getRuns().size()) {
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

        document.getElements().add(insertIdx, new PageBreakElement());
        document.getElements().add(insertIdx + 1, newP);
        currentElementIndex = insertIdx + 1;
        activeParagraph = newP;
        currentRunIndex = 0;
        currentCharOffset = 0;
        document.setModified(true);
        relayout();
    }

    public void insertBlankPage() {
        int insertIdx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();
        document.getElements().add(insertIdx, new PageBreakElement());
        Paragraph emptyP = new Paragraph();
        document.getElements().add(insertIdx + 1, emptyP);
        document.getElements().add(insertIdx + 2, new PageBreakElement());
        Paragraph nextP = new Paragraph();
        document.getElements().add(insertIdx + 3, nextP);
        currentElementIndex = insertIdx + 3;
        activeParagraph = nextP;
        currentRunIndex = 0;
        currentCharOffset = 0;
        document.setModified(true);
        relayout();
    }

    public void insertHorizontalRule() {
        Paragraph p = new Paragraph("____________________________________________________________");
        p.setSpaceBefore(6.0);
        p.setSpaceAfter(6.0);
        int idx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();
        document.getElements().add(idx, p);
        Paragraph nextP = new Paragraph();
        document.getElements().add(idx + 1, nextP);
        currentElementIndex = idx + 1;
        activeParagraph = nextP;
        document.setModified(true);
        relayout();
    }

    public void insertDateTime() {
        insertText(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    private void showInsertTableDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Insert Table");
        dialog.setHeaderText("Specify table dimensions");

        Spinner<Integer> colSpinner = new Spinner<>(1, 50, 3);
        colSpinner.setEditable(true);
        Spinner<Integer> rowSpinner = new Spinner<>(1, 100, 3);
        rowSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Columns:"), 0, 0);
        grid.add(colSpinner, 1, 0);
        grid.add(new Label("Rows:"), 0, 1);
        grid.add(rowSpinner, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            int rows = rowSpinner.getValue();
            int cols = colSpinner.getValue();
            DocumentTable table = new DocumentTable(rows, cols);
            int idx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();
            document.getElements().add(idx, table);
            Paragraph afterP = new Paragraph();
            document.getElements().add(idx + 1, afterP);
            currentElementIndex = idx + 1;
            activeParagraph = afterP;
            activeTable = table;
            document.setModified(true);
            relayout();
        }
    }

    private void insertTableRow(boolean below) {
        if (activeTable == null) return;
        int target = below ? (activeRowIndex + 1) : Math.max(0, activeRowIndex);
        activeTable.insertRow(target);
        document.setModified(true);
        relayout();
    }

    private void deleteTableRow() {
        if (activeTable == null) return;
        activeTable.removeRow(activeRowIndex);
        document.setModified(true);
        relayout();
    }

    private void insertTableColumn(boolean right) {
        if (activeTable == null) return;
        int target = right ? (activeColIndex + 1) : Math.max(0, activeColIndex);
        activeTable.insertColumn(target);
        document.setModified(true);
        relayout();
    }

    private void deleteTableColumn() {
        if (activeTable == null) return;
        activeTable.removeColumn(activeColIndex);
        document.setModified(true);
        relayout();
    }

    private void deleteActiveTable() {
        if (activeTable == null) return;
        document.getElements().remove(activeTable);
        activeTable = null;
        activeCell = null;
        document.setModified(true);
        relayout();
    }

    private void applyCellShading(Color color) {
        if (activeCell != null) {
            activeCell.setBackgroundColor(color);
            document.setModified(true);
            render();
        }
    }

    private void applyTableBorderColor(Color color) {
        if (activeTable != null) {
            activeTable.setBorderColor(color);
            document.setModified(true);
            render();
        }
    }

    private void applyTableBorderWidth(double width) {
        if (activeTable != null) {
            activeTable.setBorderWidth(width);
            document.setModified(true);
            render();
        }
    }

    private void applyTableAlignment(DocumentTable.Alignment align) {
        if (activeTable != null) {
            activeTable.setAlignment(align);
            document.setModified(true);
            relayout();
        }
    }

    private void applyCellVerticalAlignment(DocumentTableCell.VAlign va) {
        if (activeCell != null) {
            activeCell.setVerticalAlignment(va);
            document.setModified(true);
            relayout();
        }
    }

    private void changeFontSize(double delta) {
        Paragraph p = getActiveParagraph();
        if (p == null) return;
        double currentSz = 11.0;
        if (!p.getRuns().isEmpty()) {
            currentSz = p.getRuns().get(Math.min(currentRunIndex, p.getRuns().size() - 1)).getFontSize();
        }
        double newSz = Math.max(4.0, Math.min(144.0, currentSz + delta));
        sizeCombo.setValue(newSz);
        applyRunStyle(r -> r.setFontSize(newSz));
    }

    private void clearSelectionFormatting() {
        applyRunStyle(r -> {
            r.setBold(false);
            r.setItalic(false);
            r.setUnderline(false);
            r.setStrikethrough(false);
            r.setFontFamily(document != null ? document.getDetectedFontFamily() : "Calibri");
            r.setFontSize(document != null ? document.getDefaultFontSize() : 11.0);
            r.setTextColor(Color.BLACK);
            r.setHighlightColor(null);
        });
    }

    private void adjustIndent(double delta) {
        Paragraph p = getActiveParagraph();
        if (p != null) {
            p.setLeftIndent(Math.max(0.0, p.getLeftIndent() + delta));
            document.setModified(true);
            relayout();
        }
    }

    public double getCurrentZoom() {
        return currentZoom;
    }

    public void relayout() {
        pages.clear();
        pages.addAll(LayoutEngine.layout(document));

        double pageW = document.getPageSetup().getPageWidth() * currentZoom;
        double pageH = document.getPageSetup().getPageHeight() * currentZoom;
        double gap = 24.0 * currentZoom;
        double totalH = pages.isEmpty()
                ? pageH + 2 * gap
                : pages.size() * (pageH + gap) + gap;
        double totalW = pageW + 48.0 * currentZoom;

        // Avoid Prism RTTexture NPE: never request a canvas larger than GPU max texture
        double safeW = Math.max(1.0, Math.min(totalW, MAX_CANVAS_DIMENSION));
        double safeH = Math.max(1.0, Math.min(totalH, MAX_CANVAS_DIMENSION));
        canvas.setWidth(safeW);
        canvas.setHeight(safeH);

        updateCaretFromPosition();
        updateStatus();
        render();
        rulerBar.redraw();
    }

    public void render() {
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        if (cw <= 0 || ch <= 0 || Double.isNaN(cw) || Double.isNaN(ch)) {
            return;
        }

        GraphicsContext gc;
        try {
            gc = canvas.getGraphicsContext2D();
        } catch (Exception ex) {
            return;
        }

        try {
            gc.clearRect(0, 0, cw, ch);

            gc.save();
            gc.scale(currentZoom, currentZoom);

            double pageW = document.getPageSetup().getPageWidth();
            double pageH = document.getPageSetup().getPageHeight();
            double gap = 24.0;
            double pageX = (cw / currentZoom - pageW) / 2.0;
            double currentY = gap;
            // Logical height available for drawing (canvas is capped)
            double maxDrawY = ch / Math.max(0.01, currentZoom);

            for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
                // Stop before overflowing the capped canvas (prevents RTTexture failure mid-paint)
                if (currentY > maxDrawY) {
                    break;
                }
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

                // 4. Draw images that are behind text first
                for (LayoutPage.ImageBox imgBox : page.getImageBoxes()) {
                    if (imgBox.behindText) drawImageBox(gc, imgBox, pageX, currentY);
                }

                // 5. Draw Lines
                for (LayoutLine line : page.getLines()) {
                    drawLine(gc, line, pageX, currentY);
                }

                // 6. Draw Tables
                for (LayoutPage.TableBox tBox : page.getTableBoxes()) {
                    drawTableBox(gc, tBox, pageX, currentY);
                }

                // 7. Draw floating / inline images in front of text
                for (LayoutPage.ImageBox imgBox : page.getImageBoxes()) {
                    if (!imgBox.behindText) drawImageBox(gc, imgBox, pageX, currentY);
                }

                currentY += pageH + gap;
            }

            // Draw Caret (only if still inside canvas)
            if (caretVisible && caretY >= 0 && caretY < maxDrawY) {
                gc.setStroke(Color.rgb(30, 41, 59));
                gc.setLineWidth(1.6);
                gc.strokeLine(caretX, caretY, caretX, caretY + caretHeight);
            }

            gc.restore();
        } catch (Exception ex) {
            // Swallow Prism/canvas paint failures on oversized docs so the app stays up
            System.err.println("Writer render skipped: " + ex.getMessage());
        }
    }

    private void drawImageBox(GraphicsContext gc, LayoutPage.ImageBox imgBox, double pageX, double pageY) {
        if (imgBox == null || imgBox.image == null || imgBox.image.isError()) return;
        if (imgBox.width <= 0 || imgBox.height <= 0) return;
        try {
            double ix = pageX + imgBox.x;
            double iy = pageY + imgBox.y;
            if (imgBox.rotationDegrees != 0) {
                gc.save();
                double cx = ix + imgBox.width / 2.0;
                double cy = iy + imgBox.height / 2.0;
                gc.translate(cx, cy);
                gc.rotate(imgBox.rotationDegrees);
                gc.drawImage(imgBox.image, -imgBox.width / 2.0, -imgBox.height / 2.0, imgBox.width, imgBox.height);
                gc.restore();
            } else {
                gc.drawImage(imgBox.image, ix, iy, imgBox.width, imgBox.height);
            }
        } catch (Exception ignored) {
            // broken image / graphics context — skip
        }
    }

    private void drawLine(GraphicsContext gc, LayoutLine line, double pageX, double pageY) {
        gc.setTextBaseline(VPos.BASELINE);

        // Selection highlight for this line's paragraph
        if (hasSelection && line.getParagraph() != null) {
            int elemIdx = document.getElements().indexOf(line.getParagraph());
            int a = Math.min(selStartElem, selEndElem);
            int b = Math.max(selStartElem, selEndElem);
            if (elemIdx >= a && elemIdx <= b) {
                gc.setFill(Color.rgb(147, 197, 253, 0.45)); // light blue selection
                double x0 = pageX + line.getX();
                double w = Math.max(line.getWidth(), 4);
                // Full-line selection when paragraph is inside range (simple but visible)
                if (a != b || elemIdx != a) {
                    gc.fillRect(x0, pageY + line.getY(), w, line.getHeight());
                } else {
                    // Same paragraph: approximate by full line when any selection exists
                    gc.fillRect(x0, pageY + line.getY(), w, line.getHeight());
                }
            }
        }

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
        for (LayoutPage.TableCellBox cBox : tBox.cells) {
            double cx = pageX + cBox.x;
            double cy = pageY + cBox.y;

            // 1. Shading / background
            if (cBox.cell.getBackgroundColor() != null) {
                gc.setFill(cBox.cell.getBackgroundColor());
                gc.fillRect(cx, cy, cBox.width, cBox.height);
            }

            // 2. Borders
            if (tBox.borderWidth > 0 && tBox.borderColor != null) {
                gc.setStroke(tBox.borderColor);
                gc.setLineWidth(tBox.borderWidth);

                if (cBox.cell.isBorderTop() && cBox.cell.isBorderBottom()
                        && cBox.cell.isBorderLeft() && cBox.cell.isBorderRight()) {
                    gc.strokeRect(cx, cy, cBox.width, cBox.height);
                } else {
                    if (cBox.cell.isBorderTop()) gc.strokeLine(cx, cy, cx + cBox.width, cy);
                    if (cBox.cell.isBorderBottom()) gc.strokeLine(cx, cy + cBox.height, cx + cBox.width, cy + cBox.height);
                    if (cBox.cell.isBorderLeft()) gc.strokeLine(cx, cy, cx, cy + cBox.height);
                    if (cBox.cell.isBorderRight()) gc.strokeLine(cx + cBox.width, cy, cx + cBox.width, cy + cBox.height);
                }
            }

            // 3. Cell text lines
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
        updateToolbarFromSelection();
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
        updateToolbarFromSelection();
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
        // Also consider text inside table cells so table content is selectable/caret-navigable
        for (LayoutPage.TableBox tBox : page.getTableBoxes()) {
            for (LayoutPage.TableCellBox cBox : tBox.cells) {
                for (LayoutLine line : cBox.lines) {
                    double lineY = pageTop + line.getY();
                    double dist = Math.abs(mouseY - (lineY + line.getHeight() / 2.0));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestLine = line;
                    }
                }
            }
        }

        if (bestLine != null) {
            Paragraph p = bestLine.getParagraph();
            activeParagraph = p;
            int elemIdx = document.getElements().indexOf(p);
            if (elemIdx >= 0) {
                currentElementIndex = elemIdx;
            }

            activeTable = null;
            activeCell = null;
            activeRowIndex = -1;
            activeColIndex = -1;
            for (int e = 0; e < document.getElements().size(); e++) {
                DocumentElement el = document.getElements().get(e);
                if (el instanceof DocumentTable table) {
                    for (int r = 0; r < table.getRows().size(); r++) {
                        DocumentTableRow row = table.getRows().get(r);
                        for (int c = 0; c < row.getCells().size(); c++) {
                            DocumentTableCell cell = row.getCells().get(c);
                            if (cell.getParagraphs().contains(p)) {
                                activeTable = table;
                                activeCell = cell;
                                activeRowIndex = r;
                                activeColIndex = c;
                                currentElementIndex = e;
                                break;
                            }
                        }
                    }
                }
            }

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

        if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
            insertPageBreak();
            e.consume();
            return;
        }

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

            if (activeCell != null) {
                int pIdx = activeCell.getParagraphs().indexOf(p);
                if (pIdx >= 0) {
                    activeCell.getParagraphs().add(pIdx + 1, newP);
                } else {
                    activeCell.addParagraph(newP);
                }
            } else {
                int insertIdx = currentElementIndex >= 0 ? currentElementIndex + 1 : document.getElements().size();
                if (insertIdx <= document.getElements().size()) {
                    document.getElements().add(insertIdx, newP);
                } else {
                    document.addElement(newP);
                }
                currentElementIndex = insertIdx;
            }

            activeParagraph = newP;
            currentRunIndex = 0;
            currentCharOffset = 0;
            document.setModified(true);
            relayout();
            e.consume();
            return;

        } else if (e.getCode() == KeyCode.TAB) {
            insertText("\t");
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
            moveCaretVertical(-1);
            e.consume();

        } else if (e.getCode() == KeyCode.DOWN) {
            moveCaretVertical(1);
            e.consume();
        }
    }

    /**
     * Move caret to the previous/next layout line and clamp inside the document.
     */
    private void moveCaretVertical(int direction) {
        if (pages.isEmpty()) return;

        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double pageX = (canvas.getWidth() / currentZoom - pageW) / 2.0;

        // Build flat list of (pageIndex, line) for navigation
        record LineRef(int pageIndex, LayoutLine line, double absY) {
        }
        java.util.ArrayList<LineRef> all = new java.util.ArrayList<>();
        for (int pi = 0; pi < pages.size(); pi++) {
            LayoutPage page = pages.get(pi);
            double pageTop = gap + pi * (pageH + gap);
            for (LayoutLine line : page.getLines()) {
                all.add(new LineRef(pi, line, pageTop + line.getY()));
            }
        }
        if (all.isEmpty()) {
            // Clamp visual caret to first page content area
            caretY = Math.max(gap + document.getPageSetup().getMarginTop(),
                    Math.min(caretY, gap + pageH - document.getPageSetup().getMarginBottom() - caretHeight));
            caretVisible = true;
            render();
            return;
        }

        // Find closest line to current caretY
        int idx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            double d = Math.abs(all.get(i).absY - caretY);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        int next = Math.max(0, Math.min(all.size() - 1, idx + direction));
        LineRef ref = all.get(next);
        LayoutLine line = ref.line;
        Paragraph p = line.getParagraph();
        currentElementIndex = document.getElements().indexOf(p);
        if (currentElementIndex < 0) currentElementIndex = 0;
        currentRunIndex = 0;
        currentCharOffset = 0;
        if (!p.getRuns().isEmpty()) {
            // Keep roughly same X: find char under caretX
            hitTestCaret(caretX, ref.absY + line.getHeight() / 2.0);
        } else {
            caretHeight = Math.max(12, line.getHeight() * 0.85);
            caretY = ref.absY + (line.getHeight() - caretHeight) / 2.0;
            caretX = pageX + line.getX();
        }
        // Hard clamp inside document canvas content
        double minY = gap + document.getPageSetup().getMarginTop();
        double maxY = gap + pages.size() * (pageH + gap) - gap
                - document.getPageSetup().getMarginBottom() - caretHeight;
        caretY = Math.max(minY, Math.min(caretY, maxY));
        caretVisible = true;
        render();
    }

    private void updateCaretFromPosition() {
        Paragraph p = getActiveParagraph();
        if (p == null) return;
        updateToolbarFromSelection();

        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double pageX = (canvas.getWidth() / currentZoom - pageW) / 2.0;

        TextRun targetRun = (!p.getRuns().isEmpty() && currentRunIndex < p.getRuns().size())
                ? p.getRuns().get(currentRunIndex) : null;

        for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
            LayoutPage page = pages.get(pIdx);
            double pageTop = gap + pIdx * (pageH + gap);

            for (LayoutLine line : page.getLines()) {
                if (line.getParagraph() == p) {
                    caretHeight = Math.max(12, line.getHeight() * 0.85);
                    caretY = pageTop + line.getY() + (line.getHeight() - caretHeight) / 2.0;

                    if (targetRun == null || line.getSegments().isEmpty()) {
                        caretX = pageX + line.getX();
                        caretVisible = true;
                        return;
                    }

                    for (LayoutLine.LineSegment seg : line.getSegments()) {
                        if (seg.getRun() == targetRun) {
                            int len = seg.getText().length();
                            int clampedOffset = Math.max(0, Math.min(currentCharOffset, len));
                            String prefix = seg.getText().substring(0, clampedOffset);
                            double prefixW = FontManager.getInstance().measureWidth(prefix, seg.getFont());
                            caretX = pageX + line.getX() + seg.getX() + prefixW;
                            caretVisible = true;
                            return;
                        }
                    }
                }
            }
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
                updateCaretFromPosition();
                render();
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
        updateCaretFromPosition();
        caretVisible = true;
        render();
    }

    public void insertText(String str) {
        if (str == null || str.isEmpty()) return;

        Paragraph p = getActiveParagraph();
        if (p == null) {
            p = new Paragraph();
            document.addElement(p);
            currentElementIndex = document.getElements().indexOf(p);
        }

        if (p.getRuns().isEmpty()) {
            TextRun r = new TextRun(str);
            p.addRun(r);
            currentRunIndex = 0;
            currentCharOffset = str.length();
        } else {
            currentRunIndex = Math.min(currentRunIndex, p.getRuns().size() - 1);
            TextRun run = p.getRuns().get(currentRunIndex);
            String txt = run.getText();
            currentCharOffset = Math.max(0, Math.min(currentCharOffset, txt.length()));
            run.setText(txt.substring(0, currentCharOffset) + str + txt.substring(currentCharOffset));
            currentCharOffset += str.length();
        }

        document.setModified(true);
        relayout();
    }

    private void handleKeyTyped(KeyEvent e) {
        String ch = e.getCharacter();
        if (ch == null || ch.isEmpty() || (ch.charAt(0) < 32 && ch.charAt(0) != '\t')) return;
        insertText(ch);
    }

    // --- Search & Replace Implementation ---

    private void updateStatus() {
        statusStatsLabel.setText(String.format("Pages: %d | Words: %d | Characters: %d",
                pages.size(), document.getWordCount(), document.getCharacterCount()));
    }

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

    @Override
    public DocumentModel getDocument() {
        return document;
    }

    // --- DocumentView Interface Implementations ---

    public WriterDocument getWriterDocument() {
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
}
