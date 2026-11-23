package app.vercel.originsd.oqteris.pdf.print;

import app.vercel.originsd.oqteris.pdf.render.PdfPageRenderer;
import app.vercel.originsd.oqteris.ui.ThemeManager;
import app.vercel.originsd.oqteris.ui.icon.AppIcons;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.kordamp.ikonli.materialdesign2.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Foxit PDF-style full-featured print dialog with live print preview.
 *
 * <p>Layout: left sidebar = consolidated print options panels; center = live page preview
 * fitting the page area with document & sheet specifications at the top;
 * bottom bar = summary + Print/Cancel buttons.</p>
 */
public class PrintPreviewDialog extends Stage {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------
    private final PDDocument    pdDocument;
    private final PdfPageRenderer renderer;
    private final int           userRotation;
    private final int           currentPage;
    private final PrintOptions  opts  = new PrintOptions();
    private final PdfPrintService printService;

    // -------------------------------------------------------------------------
    // Preview state
    // -------------------------------------------------------------------------
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "oqteris-print-preview");
        t.setDaemon(true);
        return t;
    });
    private final PauseTransition debounceTimer = new PauseTransition(Duration.millis(75));
    private final AtomicInteger previewVersion = new AtomicInteger(0);
    private boolean isInitializing = true;

    private int previewSheetIndex = 0;
    private int totalPreviewSheets = 1;

    // -------------------------------------------------------------------------
    // UI references
    // -------------------------------------------------------------------------
    private final ImageView    previewImage   = new ImageView();
    private final Label        previewPageLabel = new Label("Sheet 1 of 1");
    private final Label        pageInfoLabel    = new Label("Page Information");
    private final ProgressIndicator previewSpinner = new ProgressIndicator();
    private final StackPane    frameBox       = new StackPane(previewImage);
    private final StackPane    previewArea    = new StackPane() {
        @Override
        protected double computePrefWidth(double height) {
            return 520;
        }
        @Override
        protected double computePrefHeight(double width) {
            return 520;
        }
        @Override
        protected double computeMinWidth(double height) {
            return 120;
        }
        @Override
        protected double computeMinHeight(double width) {
            return 120;
        }
        @Override
        protected void layoutChildren() {
            layoutPreview();
        }
    };
    private final Label        printerInfoLabel = new Label();

    // Printer & Copies
    private final ComboBox<Printer> printerCombo = new ComboBox<>();
    private final Spinner<Integer> copiesSpinner  = new Spinner<>(1, 999, 1);
    private final CheckBox    collateCheck    = new CheckBox("Collate");
    private final CheckBox    reverseCheck    = new CheckBox("Reverse");

    // Page range
    private final ToggleGroup  rangeGroup     = new ToggleGroup();
    private final RadioButton  rangeAll       = new RadioButton("All");
    private final RadioButton  rangeCurrent   = new RadioButton("Current page");
    private final RadioButton  rangeCustom    = new RadioButton("Pages:");
    private final TextField    customRangeField = new TextField();

    // Page Handling Mode (Consolidated: Scale, Multiple, Booklet)
    private final ToggleGroup  handlingGroup  = new ToggleGroup();
    private final RadioButton  handleScaleRadio    = new RadioButton("Fit / Scale");
    private final RadioButton  handleMultipleRadio = new RadioButton("Multiple Pages");
    private final RadioButton  handleBookletRadio  = new RadioButton("Booklet");

    // Scaling & Margins sub-controls
    private final ToggleGroup fitGroup        = new ToggleGroup();
    private final RadioButton fitFitPage      = new RadioButton("Fit Page");
    private final RadioButton fitActualSize   = new RadioButton("Actual Size (100%)");
    private final RadioButton fitShrink       = new RadioButton("Shrink Oversized");
    private final RadioButton fitCustom       = new RadioButton("Custom Scale:");
    private final Spinner<Integer> scaleSpinner = new Spinner<>(10, 400, 100);
    private final ComboBox<PrintOptions.MarginMode> marginCombo = new ComboBox<>();
    private final Spinner<Double> customMarginSpinner = new Spinner<>(0.0, 50.0, 10.0, 1.0);
    private final CheckBox    autoRotateCheck  = new CheckBox("Auto-rotate to fit paper");
    private final CheckBox    centerCheck      = new CheckBox("Center on page");

    // Multiple pages per sheet sub-controls
    private final ComboBox<String> nupCombo = new ComboBox<>();
    private final ComboBox<String> nupOrderCombo = new ComboBox<>();
    private final CheckBox    nupBordersCheck = new CheckBox("Draw page borders");

    // Booklet sub-controls
    private final ComboBox<String> bookletSubsetCombo = new ComboBox<>();
    private final CheckBox    bookletShortEdge = new CheckBox("Flip on short edge (calendar)");

    // Orientation & Duplex
    private final ToggleGroup orientGroup     = new ToggleGroup();
    private final RadioButton orientAuto      = new RadioButton("Auto");
    private final RadioButton orientPortrait  = new RadioButton("Portrait");
    private final RadioButton orientLandscape = new RadioButton("Landscape");

    private final ToggleGroup duplexGroup     = new ToggleGroup();
    private final RadioButton duplexNone      = new RadioButton("Simplex (one-sided)");
    private final RadioButton duplexLong      = new RadioButton("Flip on long edge");
    private final RadioButton duplexShort     = new RadioButton("Flip on short edge");

    // Content
    private final CheckBox    annotCheck      = new CheckBox("Annotations");
    private final CheckBox    formCheck       = new CheckBox("Form fields");
    private final CheckBox    commentCheck    = new CheckBox("Comments");
    private final CheckBox    grayscaleCheck  = new CheckBox("Grayscale (Black & White)");

    // =========================================================================
    // Constructor
    // =========================================================================

    public PrintPreviewDialog(Window owner, PDDocument pdDocument, PdfPageRenderer renderer,
                              int userRotation, int currentPage) {
        this.pdDocument   = pdDocument;
        this.renderer     = renderer;
        this.userRotation = userRotation;
        this.currentPage  = currentPage;
        this.printService = new PdfPrintService(pdDocument, renderer, userRotation);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Print – Oqteris PDF");
        setMinWidth(920);
        setMinHeight(620);
        setWidth(980);
        setHeight(680);

        Scene scene = new Scene(buildRoot());
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);

        // Load printer list and apply initial UI state before enabling listeners
        initPrinters();
        applyInitialState();
        wireListeners();
        isInitializing = false;

        // Trigger initial preview render
        Platform.runLater(this::schedulePreviewRefresh);

        setOnHidden(e -> {
            debounceTimer.stop();
            previewExecutor.shutdownNow();
        });
    }

    // =========================================================================
    // Root layout
    // =========================================================================

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("print-dialog-root");

        // Top header bar
        root.setTop(buildHeaderBar());

        // Left sidebar: options
        root.setLeft(buildOptionsPanel());

        // Center: preview
        root.setCenter(buildPreviewPanel());

        // Bottom: footer bar with Print / Cancel
        root.setBottom(buildFooterBar());

        return root;
    }

    // =========================================================================
    // Header bar
    // =========================================================================

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("print-header-bar");
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setAlignment(Pos.CENTER_LEFT);

        Node printerIcon = AppIcons.of(MaterialDesignP.PRINTER, 22);
        Label title = new Label("Print");
        title.getStyleClass().add("print-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(printerIcon, title, spacer);
        return bar;
    }

    // =========================================================================
    // Left options panel
    // =========================================================================

    private ScrollPane buildOptionsPanel() {
        VBox panel = new VBox(0);
        panel.getStyleClass().add("print-options-panel");
        panel.setPrefWidth(360);
        panel.setMinWidth(330);
        panel.setMaxWidth(390);
        panel.setMinHeight(Region.USE_PREF_SIZE);

        panel.getChildren().addAll(
                buildPrinterAndCopiesSection(),
                separator(),
                buildPageRangeSection(),
                separator(),
                buildPageHandlingSection(),
                separator(),
                buildOrientationAndDuplexSection(),
                separator(),
                buildContentSection()
        );

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("print-options-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setPrefWidth(360);
        scroll.setMinWidth(330);
        scroll.setMaxWidth(390);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Ensure mouse wheel scrolling works reliably over all child controls
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                double delta = event.getDeltaY();
                double contentHeight = panel.getBoundsInLocal().getHeight();
                double viewportHeight = scroll.getViewportBounds().getHeight();
                if (contentHeight > viewportHeight) {
                    double vvalue = scroll.getVvalue() - (delta * 2.5) / (contentHeight - viewportHeight);
                    scroll.setVvalue(Math.max(0.0, Math.min(1.0, vvalue)));
                    event.consume();
                }
            }
        });

        return scroll;
    }

    // ---- Section 1: Printer & Copies (Foxit Top Section) ----
    private VBox buildPrinterAndCopiesSection() {
        VBox sec = section("Printer & Copies");

        printerCombo.setMaxWidth(Double.MAX_VALUE);
        printerCombo.setPlaceholder(new Label("No printers found"));
        printerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Printer printer) {
                return printer != null ? printer.getName() : "";
            }
            @Override public Printer fromString(String string) {
                return null;
            }
        });
        printerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Printer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setGraphic(null);
            }
        });

        Button propertiesBtn = new Button("Properties…");
        propertiesBtn.getStyleClass().add("action-btn-secondary");
        propertiesBtn.setOnAction(e -> showPrinterProperties());

        HBox printerRow = new HBox(8, printerCombo, propertiesBtn);
        HBox.setHgrow(printerCombo, Priority.ALWAYS);
        printerRow.setAlignment(Pos.CENTER_LEFT);

        printerInfoLabel.getStyleClass().add("text-muted");
        printerInfoLabel.setWrapText(true);

        // Copies row - placed prominently at the top
        copiesSpinner.setEditable(true);
        copiesSpinner.setPrefWidth(72);
        copiesSpinner.getStyleClass().add("print-spinner");

        collateCheck.setSelected(true);
        collateCheck.getStyleClass().add("print-checkbox");
        reverseCheck.getStyleClass().add("print-checkbox");

        HBox copiesRow = new HBox(10,
                new Label("Copies:"), copiesSpinner,
                collateCheck, reverseCheck
        );
        copiesRow.setAlignment(Pos.CENTER_LEFT);
        copiesRow.setPadding(new Insets(2, 0, 0, 0));

        sec.getChildren().addAll(printerRow, printerInfoLabel, copiesRow);
        return sec;
    }

    // ---- Section 2: Page Range ----
    private VBox buildPageRangeSection() {
        VBox sec = section("Page Range");

        rangeAll.setToggleGroup(rangeGroup);
        rangeCurrent.setToggleGroup(rangeGroup);
        rangeCustom.setToggleGroup(rangeGroup);
        rangeAll.setSelected(true);

        customRangeField.setPromptText("e.g. 1, 3, 5-8");
        customRangeField.setDisable(true);
        customRangeField.getStyleClass().add("print-text-field");
        HBox.setHgrow(customRangeField, Priority.ALWAYS);
        HBox customRow = new HBox(8, rangeCustom, customRangeField);
        customRow.setAlignment(Pos.CENTER_LEFT);

        rangeCustom.selectedProperty().addListener((o, ov, nv) -> customRangeField.setDisable(!nv));

        Label hint = new Label("Example: 1, 3, 5-8  or  2-10");
        hint.getStyleClass().add("print-hint-label");

        sec.getChildren().addAll(rangeAll, rangeCurrent, customRow, hint);
        return sec;
    }

    // ---- Section 3: Consolidated Page Handling (Scale, Multiple, Booklet) ----
    private VBox buildPageHandlingSection() {
        VBox sec = section("Page Handling");

        handleScaleRadio.setToggleGroup(handlingGroup);
        handleMultipleRadio.setToggleGroup(handlingGroup);
        handleBookletRadio.setToggleGroup(handlingGroup);
        handleScaleRadio.setSelected(true);

        HBox modeRow = new HBox(12, handleScaleRadio, handleMultipleRadio, handleBookletRadio);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        modeRow.setPadding(new Insets(0, 0, 6, 0));

        // Sub-panel A: Scale & Sizing
        VBox scaleBox = new VBox(6);

        fitFitPage.setToggleGroup(fitGroup);
        fitActualSize.setToggleGroup(fitGroup);
        fitShrink.setToggleGroup(fitGroup);
        fitCustom.setToggleGroup(fitGroup);
        fitFitPage.setSelected(true);

        scaleSpinner.setEditable(true);
        scaleSpinner.setPrefWidth(72);
        scaleSpinner.setDisable(true);
        scaleSpinner.getStyleClass().add("print-spinner");
        fitCustom.selectedProperty().addListener((o, ov, nv) -> scaleSpinner.setDisable(!nv));

        HBox customScaleRow = new HBox(8, fitCustom, scaleSpinner, new Label("%"));
        customScaleRow.setAlignment(Pos.CENTER_LEFT);

        // Margins controls
        marginCombo.getItems().addAll(PrintOptions.MarginMode.values());
        marginCombo.getSelectionModel().select(PrintOptions.MarginMode.NONE);
        marginCombo.setPrefWidth(125);

        customMarginSpinner.setEditable(true);
        customMarginSpinner.setPrefWidth(72);
        customMarginSpinner.setDisable(true);
        marginCombo.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            customMarginSpinner.setDisable(nv != PrintOptions.MarginMode.CUSTOM);
        });

        HBox marginRow = new HBox(8,
                new Label("Print Margin:"), marginCombo,
                customMarginSpinner, new Label("mm")
        );
        marginRow.setAlignment(Pos.CENTER_LEFT);

        autoRotateCheck.setSelected(true);
        autoRotateCheck.getStyleClass().add("print-checkbox");
        centerCheck.setSelected(true);
        centerCheck.getStyleClass().add("print-checkbox");

        HBox autoCenterRow = new HBox(14, autoRotateCheck, centerCheck);
        autoCenterRow.setAlignment(Pos.CENTER_LEFT);

        scaleBox.getChildren().addAll(
                fitFitPage, fitActualSize, fitShrink, customScaleRow,
                marginRow, autoCenterRow
        );

        // Sub-panel B: Multiple Pages per Sheet (N-Up)
        VBox multipleBox = new VBox(6);

        nupCombo.getItems().addAll(
                "2 pages per sheet",
                "4 pages per sheet",
                "6 pages per sheet",
                "9 pages per sheet",
                "16 pages per sheet"
        );
        nupCombo.getSelectionModel().select(0);
        nupCombo.setMaxWidth(Double.MAX_VALUE);

        nupOrderCombo.getItems().addAll("Horizontal first (Left to Right)", "Vertical first (Top to Bottom)");
        nupOrderCombo.getSelectionModel().select(0);
        nupOrderCombo.setMaxWidth(Double.MAX_VALUE);

        nupBordersCheck.setSelected(true);
        nupBordersCheck.getStyleClass().add("print-checkbox");

        HBox nupRow = new HBox(8, new Label("Pages per sheet:"), nupCombo);
        nupRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nupCombo, Priority.ALWAYS);

        HBox orderRow = new HBox(8, new Label("Page order:"), nupOrderCombo);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nupOrderCombo, Priority.ALWAYS);

        multipleBox.getChildren().addAll(nupRow, orderRow, nupBordersCheck);

        // Sub-panel C: Booklet Printing
        VBox bookletBox = new VBox(6);

        bookletSubsetCombo.getItems().addAll(
                "Both sides (Full booklet)",
                "Front side only",
                "Back side only"
        );
        bookletSubsetCombo.getSelectionModel().select(0);
        bookletSubsetCombo.setMaxWidth(Double.MAX_VALUE);

        bookletShortEdge.getStyleClass().add("print-checkbox");

        HBox bRow = new HBox(8, new Label("Booklet subset:"), bookletSubsetCombo);
        bRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bookletSubsetCombo, Priority.ALWAYS);

        Label bookletHint = new Label("Saddle-stitch binding format for folded booklets.");
        bookletHint.getStyleClass().add("print-hint-label");

        bookletBox.getChildren().addAll(bRow, bookletShortEdge, bookletHint);

        // Dynamic visibility switching based on selected Page Handling mode
        scaleBox.visibleProperty().bind(handleScaleRadio.selectedProperty());
        scaleBox.managedProperty().bind(scaleBox.visibleProperty());

        multipleBox.visibleProperty().bind(handleMultipleRadio.selectedProperty());
        multipleBox.managedProperty().bind(multipleBox.visibleProperty());

        bookletBox.visibleProperty().bind(handleBookletRadio.selectedProperty());
        bookletBox.managedProperty().bind(bookletBox.visibleProperty());

        sec.getChildren().addAll(modeRow, scaleBox, multipleBox, bookletBox);
        return sec;
    }

    // ---- Section 4: Orientation & Duplex ----
    private VBox buildOrientationAndDuplexSection() {
        VBox sec = section("Orientation & Two-Sided");

        orientAuto.setToggleGroup(orientGroup);
        orientPortrait.setToggleGroup(orientGroup);
        orientLandscape.setToggleGroup(orientGroup);
        orientAuto.setSelected(true);

        HBox orientRow = new HBox(16, orientAuto, orientPortrait, orientLandscape);
        orientRow.setAlignment(Pos.CENTER_LEFT);

        duplexNone.setToggleGroup(duplexGroup);
        duplexLong.setToggleGroup(duplexGroup);
        duplexShort.setToggleGroup(duplexGroup);
        duplexNone.setSelected(true);

        VBox duplexBox = new VBox(4, duplexNone, duplexLong, duplexShort);

        sec.getChildren().addAll(
                new Label("Page Orientation:"), orientRow,
                new Separator(),
                new Label("Two-Sided (Duplex):"), duplexBox
        );
        return sec;
    }

    // ---- Section 5: Content to Print ----
    private VBox buildContentSection() {
        VBox sec = section("Content & Output");
        annotCheck.setSelected(true);
        formCheck.setSelected(true);
        annotCheck.getStyleClass().add("print-checkbox");
        formCheck.getStyleClass().add("print-checkbox");
        commentCheck.getStyleClass().add("print-checkbox");
        grayscaleCheck.getStyleClass().add("print-checkbox");

        HBox row1 = new HBox(14, annotCheck, formCheck, commentCheck);
        sec.getChildren().addAll(row1, new Separator(), grayscaleCheck);
        return sec;
    }

    // =========================================================================
    // Center: preview panel
    // =========================================================================

    private BorderPane buildPreviewPanel() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("print-preview-panel");

        // Top info bar with document dimensions, paper, and sheet information
        HBox topInfoBar = new HBox(10);
        topInfoBar.getStyleClass().add("print-preview-info-bar");
        topInfoBar.setAlignment(Pos.CENTER_LEFT);

        Node docIcon = AppIcons.of(MaterialDesignF.FILE_DOCUMENT_OUTLINE, 18);
        pageInfoLabel.getStyleClass().add("print-preview-info-text");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        topInfoBar.getChildren().addAll(docIcon, pageInfoLabel, topSpacer);
        pane.setTop(topInfoBar);

        // Preview image area - fits page area cleanly
        previewImage.setPreserveRatio(true);
        previewImage.setSmooth(true);

        previewSpinner.setMaxSize(40, 40);
        previewSpinner.setVisible(false);

        // White paper frame wrapped snugly around the page image
        frameBox.getStyleClass().add("print-preview-frame");
        frameBox.getChildren().setAll(previewImage);

        previewArea.getStyleClass().add("print-preview-area");
        previewArea.getChildren().setAll(frameBox, previewSpinner);

        pane.setCenter(previewArea);

        // Navigation bar at bottom of preview
        pane.setBottom(buildPreviewNavBar());

        return pane;
    }

    private HBox buildPreviewNavBar() {
        HBox nav = new HBox(10);
        nav.getStyleClass().add("print-preview-nav");
        nav.setPadding(new Insets(8, 16, 8, 16));
        nav.setAlignment(Pos.CENTER);

        Button firstBtn = new Button();
        firstBtn.setGraphic(AppIcons.of(MaterialDesignP.PAGE_FIRST, 16));
        firstBtn.getStyleClass().add("print-nav-btn");
        firstBtn.setOnAction(e -> { previewSheetIndex = 0; schedulePreviewRefresh(); });

        Button prevBtn = new Button();
        prevBtn.setGraphic(AppIcons.of(MaterialDesignC.CHEVRON_LEFT, 16));
        prevBtn.getStyleClass().add("print-nav-btn");
        prevBtn.setTooltip(new Tooltip("Previous sheet"));
        prevBtn.setOnAction(e -> navigatePreview(-1));

        Button nextBtn = new Button();
        nextBtn.setGraphic(AppIcons.of(MaterialDesignC.CHEVRON_RIGHT, 16));
        nextBtn.getStyleClass().add("print-nav-btn");
        nextBtn.setTooltip(new Tooltip("Next sheet"));
        nextBtn.setOnAction(e -> navigatePreview(1));

        Button lastBtn = new Button();
        lastBtn.setGraphic(AppIcons.of(MaterialDesignP.PAGE_LAST, 16));
        lastBtn.getStyleClass().add("print-nav-btn");
        lastBtn.setOnAction(e -> { previewSheetIndex = Math.max(0, totalPreviewSheets - 1); schedulePreviewRefresh(); });

        previewPageLabel.getStyleClass().add("print-preview-page-label");

        nav.getChildren().addAll(firstBtn, prevBtn, previewPageLabel, nextBtn, lastBtn);
        return nav;
    }

    // =========================================================================
    // Footer bar
    // =========================================================================

    private HBox buildFooterBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("print-footer-bar");
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setAlignment(Pos.CENTER_RIGHT);

        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("print-summary-label");
        updateSummaryLabel(summaryLabel);
        opts.copiesProperty().addListener((o, ov, nv) -> updateSummaryLabel(summaryLabel));
        opts.pageRangeModeProperty().addListener((o, ov, nv) -> updateSummaryLabel(summaryLabel));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("action-btn-secondary");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> {
            debounceTimer.stop();
            previewExecutor.shutdownNow();
            close();
        });

        Button printBtn = new Button("Print");
        printBtn.getStyleClass().addAll("action-btn-primary");
        printBtn.setDefaultButton(true);
        printBtn.setGraphic(AppIcons.of(MaterialDesignP.PRINTER, 16));
        printBtn.setOnAction(e -> executePrint());

        bar.getChildren().addAll(summaryLabel, spacer, cancelBtn, printBtn);
        return bar;
    }

    // =========================================================================
    // Wiring listeners
    // =========================================================================

    private void wireListeners() {
        ChangeListener<Object> refresher = (o, ov, nv) -> {
            if (!isInitializing) schedulePreviewRefresh();
        };

        // Page Range
        rangeGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            opts.setPageRangeMode(
                    nv == rangeAll ? PrintOptions.PageRangeMode.ALL :
                    nv == rangeCurrent ? PrintOptions.PageRangeMode.CURRENT :
                    PrintOptions.PageRangeMode.CUSTOM
            );
            if (!isInitializing) schedulePreviewRefresh();
        });
        customRangeField.textProperty().addListener((o, ov, nv) -> {
            opts.setCustomPageRange(nv);
            if (!isInitializing) schedulePreviewRefresh();
        });

        // Copies
        copiesSpinner.valueProperty().addListener((o, ov, nv) -> opts.setCopies(nv));
        collateCheck.selectedProperty().addListener((o, ov, nv) -> opts.setCollate(nv));
        reverseCheck.selectedProperty().addListener((o, ov, nv) -> opts.setReverseOrder(nv));

        // Page Handling Mode (Scale, Multiple, Booklet)
        handlingGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            if (nv == handleMultipleRadio) {
                opts.setPageHandlingMode(PrintOptions.PageHandlingMode.MULTIPLE);
                int idx = Math.max(0, nupCombo.getSelectionModel().getSelectedIndex());
                opts.setPagesPerSheet(switch (idx) {
                    case 1 -> PrintOptions.PagesPerSheet.FOUR;
                    case 2 -> PrintOptions.PagesPerSheet.SIX;
                    case 3 -> PrintOptions.PagesPerSheet.NINE;
                    case 4 -> PrintOptions.PagesPerSheet.SIXTEEN;
                    default -> PrintOptions.PagesPerSheet.TWO;
                });
            } else if (nv == handleBookletRadio) {
                opts.setPageHandlingMode(PrintOptions.PageHandlingMode.BOOKLET);
                int idx = Math.max(0, bookletSubsetCombo.getSelectionModel().getSelectedIndex());
                opts.setBookletSubset(switch (idx) {
                    case 1 -> PrintOptions.BookletSubset.FRONT_SIDE_ONLY;
                    case 2 -> PrintOptions.BookletSubset.BACK_SIDE_ONLY;
                    default -> PrintOptions.BookletSubset.BOTH_SIDES;
                });
            } else {
                opts.setPageHandlingMode(PrintOptions.PageHandlingMode.SCALE);
            }
            previewSheetIndex = 0;
            refresher.changed(null, null, null);
        });

        // Scaling options
        fitGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            opts.setFitMode(
                    nv == fitActualSize ? PrintOptions.FitMode.ACTUAL_SIZE :
                    nv == fitShrink     ? PrintOptions.FitMode.SHRINK_OVERSIZED :
                    nv == fitCustom     ? PrintOptions.FitMode.CUSTOM_SCALE :
                    PrintOptions.FitMode.FIT_PAGE
            );
            refresher.changed(null, null, null);
        });
        scaleSpinner.valueProperty().addListener((o, ov, nv) -> {
            opts.setCustomScalePct(nv);
            if (fitCustom.isSelected()) refresher.changed(null, null, null);
        });

        marginCombo.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                opts.setMarginMode(nv);
                refresher.changed(null, null, null);
            }
        });
        customMarginSpinner.valueProperty().addListener((o, ov, nv) -> {
            opts.setCustomMarginMm(nv);
            if (marginCombo.getValue() == PrintOptions.MarginMode.CUSTOM) {
                refresher.changed(null, null, null);
            }
        });

        autoRotateCheck.selectedProperty().addListener((o, ov, nv) -> { opts.setAutoRotate(nv); refresher.changed(null, null, null); });
        centerCheck.selectedProperty().addListener((o, ov, nv)  -> { opts.setCenterOnPage(nv); refresher.changed(null, null, null); });

        // Multiple options
        nupCombo.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            opts.setPagesPerSheet(switch (nv.intValue()) {
                case 0 -> PrintOptions.PagesPerSheet.TWO;
                case 1 -> PrintOptions.PagesPerSheet.FOUR;
                case 2 -> PrintOptions.PagesPerSheet.SIX;
                case 3 -> PrintOptions.PagesPerSheet.NINE;
                case 4 -> PrintOptions.PagesPerSheet.SIXTEEN;
                default -> PrintOptions.PagesPerSheet.TWO;
            });
            refresher.changed(null, null, null);
        });
        nupOrderCombo.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            opts.setNupHorizontalFirst(nv.intValue() == 0);
            refresher.changed(null, null, null);
        });
        nupBordersCheck.selectedProperty().addListener((o, ov, nv) -> { opts.setNupPageBorders(nv); refresher.changed(null, null, null); });

        // Booklet options
        bookletSubsetCombo.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            opts.setBookletSubset(switch (nv.intValue()) {
                case 1 -> PrintOptions.BookletSubset.FRONT_SIDE_ONLY;
                case 2 -> PrintOptions.BookletSubset.BACK_SIDE_ONLY;
                default -> PrintOptions.BookletSubset.BOTH_SIDES;
            });
            refresher.changed(null, null, null);
        });
        bookletShortEdge.selectedProperty().addListener((o, ov, nv) -> { opts.setBookletFlipOnShortEdge(nv); refresher.changed(null, null, null); });

        // Orientation & Duplex
        orientGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            opts.setOrientation(
                    nv == orientPortrait ? PrintOptions.OrientationMode.PORTRAIT :
                    nv == orientLandscape ? PrintOptions.OrientationMode.LANDSCAPE :
                    PrintOptions.OrientationMode.AUTO
            );
            refresher.changed(null, null, null);
        });

        duplexGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            opts.setDuplexMode(
                    nv == duplexLong  ? PrintOptions.DuplexMode.LONG_EDGE :
                    nv == duplexShort ? PrintOptions.DuplexMode.SHORT_EDGE :
                    PrintOptions.DuplexMode.NONE
            );
        });

        // Content
        annotCheck.selectedProperty().addListener((o, ov, nv)    -> { opts.setPrintAnnotations(nv); refresher.changed(null, null, null); });
        formCheck.selectedProperty().addListener((o, ov, nv)     -> { opts.setPrintFormFields(nv); refresher.changed(null, null, null); });
        commentCheck.selectedProperty().addListener((o, ov, nv)  -> { opts.setPrintComments(nv); refresher.changed(null, null, null); });
        grayscaleCheck.selectedProperty().addListener((o, ov, nv) -> { opts.setGrayscale(nv); refresher.changed(null, null, null); });

        printerCombo.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                opts.setPrinter(nv);
                updatePrinterInfo(nv);
                updatePageInfo();
            }
        });
    }

    // =========================================================================
    // Printer initialization
    // =========================================================================

    private void initPrinters() {
        var printers = Printer.getAllPrinters();
        printerCombo.getItems().addAll(printers);
        Printer def = Printer.getDefaultPrinter();
        if (def != null && printers.contains(def)) {
            printerCombo.getSelectionModel().select(def);
        } else if (!printers.isEmpty()) {
            printerCombo.getSelectionModel().select(0);
        }
    }

    private void updatePrinterInfo(Printer printer) {
        if (printer == null) {
            printerInfoLabel.setText("");
            return;
        }
        PrinterAttributes attr = printer.getPrinterAttributes();
        StringBuilder sb = new StringBuilder("Status: Ready");
        if (attr != null) {
            var papers = attr.getSupportedPapers();
            if (papers != null && !papers.isEmpty()) {
                sb.append("  •  Default Paper: ").append(papers.iterator().next().getName());
            }
        }
        printerInfoLabel.setText(sb.toString());
    }

    private void showPrinterProperties() {
        try {
            Printer p = printerCombo.getValue();
            if (p == null) return;
            PrinterJob job = PrinterJob.createPrinterJob(p);
            if (job != null) {
                job.showPageSetupDialog(this);
                job.cancelJob();
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Initial UI state
    // =========================================================================

    private void applyInitialState() {
        rangeAll.setSelected(true);
        handleScaleRadio.setSelected(true);
        fitFitPage.setSelected(true);
        marginCombo.getSelectionModel().select(PrintOptions.MarginMode.NONE);
        orientAuto.setSelected(true);
        duplexNone.setSelected(true);
        collateCheck.setSelected(true);
        autoRotateCheck.setSelected(true);
        centerCheck.setSelected(true);
        nupCombo.getSelectionModel().select(0);
        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.TWO);
        nupBordersCheck.setSelected(true);
        bookletSubsetCombo.getSelectionModel().select(0);
        opts.setBookletSubset(PrintOptions.BookletSubset.BOTH_SIDES);
        annotCheck.setSelected(true);
        formCheck.setSelected(true);
    }

    // =========================================================================
    // Preview rendering & sizing
    // =========================================================================

    private void schedulePreviewRefresh() {
        if (isInitializing) return;
        previewSpinner.setVisible(true);
        debounceTimer.setOnFinished(e -> startPreviewRender());
        debounceTimer.playFromStart();
    }

    private void startPreviewRender() {
        final int version = previewVersion.incrementAndGet();

        List<Integer> pageIndices = printService.resolvePageIndices(opts, currentPage);
        if (pageIndices.isEmpty()) {
            previewImage.setImage(null);
            previewSpinner.setVisible(false);
            previewPageLabel.setText("No pages");
            pageInfoLabel.setText("No pages in specified range");
            return;
        }

        // Compute total preview sheets
        if (opts.isBookletPrint()) {
            totalPreviewSheets = Math.max(1, (int) Math.ceil(pageIndices.size() / 2.0));
        } else if (opts.getPageHandlingMode() == PrintOptions.PageHandlingMode.MULTIPLE) {
            int nup = opts.nupCount();
            totalPreviewSheets = Math.max(1, (int) Math.ceil((double) pageIndices.size() / nup));
        } else {
            totalPreviewSheets = pageIndices.size();
        }
        previewSheetIndex = Math.min(previewSheetIndex, totalPreviewSheets - 1);

        final int sheetIdx = previewSheetIndex;
        final int total    = totalPreviewSheets;
        final List<Integer> indices = new ArrayList<>(pageIndices);
        final PrintOptions optsCopy = snapshotOpts();

        // Calculate sheet aspect ratio and target preview dimensions
        double sheetRatio = getSheetAspectRatio();
        double areaW = previewArea.getWidth() > 100 ? previewArea.getWidth() - 36 : 500;
        double areaH = previewArea.getHeight() > 100 ? previewArea.getHeight() - 36 : 500;

        double targetW, targetH;
        if (areaW / areaH < sheetRatio) {
            targetW = areaW;
            targetH = areaW / sheetRatio;
        } else {
            targetH = areaH;
            targetW = areaH * sheetRatio;
        }

        final double renderW = targetW;
        final double renderH = targetH;

        previewExecutor.submit(() -> {
            if (version != previewVersion.get()) return;
            Image img = renderPreviewSheet(sheetIdx, indices, optsCopy, renderW, renderH);
            if (version != previewVersion.get()) return;

            Platform.runLater(() -> {
                if (version == previewVersion.get()) {
                    previewImage.setImage(img);
                    previewSpinner.setVisible(false);
                    previewPageLabel.setText("Sheet " + (sheetIdx + 1) + " of " + total);
                    updatePageInfo();
                    previewArea.requestLayout();
                }
            });
        });
    }

    private Image renderPreviewSheet(int sheetIdx, List<Integer> pageIndices,
                                      PrintOptions opts, double previewW, double previewH) {
        int nup = opts.nupCount();
        List<Integer> chunk = new ArrayList<>();

        if (opts.isBookletPrint()) {
            int from = sheetIdx * 2;
            for (int k = 0; k < 2; k++) {
                int pi = from + k;
                chunk.add(pi < pageIndices.size() ? pageIndices.get(pi) : -1);
            }
            return printService.compositeSheetPreviewPublic(chunk, opts, previewW, previewH, true);
        } else if (opts.getPageHandlingMode() == PrintOptions.PageHandlingMode.MULTIPLE) {
            int from = sheetIdx * nup;
            for (int k = 0; k < nup; k++) {
                int pi = from + k;
                chunk.add(pi < pageIndices.size() ? pageIndices.get(pi) : -1);
            }
            return printService.compositeSheetPreviewPublic(chunk, opts, previewW, previewH, false);
        } else {
            // Single page fitting
            int pi = sheetIdx < pageIndices.size() ? pageIndices.get(sheetIdx) : 0;
            chunk.add(pi);
            return printService.compositeSheetPreviewPublic(chunk, opts, previewW, previewH, false);
        }
    }

    /**
     * Positions and sizes the preview image and paper frame snugly within the preview area,
     * maintaining the exact sheet aspect ratio and strictly preventing layout expansion loops.
     */
    private void layoutPreview() {
        double w = previewArea.getWidth();
        double h = previewArea.getHeight();
        if (w <= 0 || h <= 0) return;

        double pad = 36.0;
        double availW = Math.max(80, w - pad);
        double availH = Math.max(80, h - pad);
        double sheetRatio = getSheetAspectRatio();

        double targetW, targetH;
        if (availW / availH < sheetRatio) {
            targetW = availW;
            targetH = availW / sheetRatio;
        } else {
            targetH = availH;
            targetW = availH * sheetRatio;
        }

        targetW = Math.round(targetW);
        targetH = Math.round(targetH);

        previewImage.setFitWidth(targetW);
        previewImage.setFitHeight(targetH);

        frameBox.setPrefSize(targetW, targetH);
        frameBox.setMinSize(targetW, targetH);
        frameBox.setMaxSize(targetW, targetH);

        double x = Math.round((w - targetW) / 2.0);
        double y = Math.round((h - targetH) / 2.0);
        frameBox.resizeRelocate(x, y, targetW, targetH);

        if (previewSpinner.isVisible()) {
            double sw = 40;
            double sh = 40;
            previewSpinner.resizeRelocate(Math.round((w - sw) / 2.0), Math.round((h - sh) / 2.0), sw, sh);
        }
    }

    private double getSheetAspectRatio() {
        int pageIndex = Math.max(0, Math.min(pdDocument.getNumberOfPages() - 1, currentPage));
        PDPage page = pdDocument.getPage(pageIndex);
        PDRectangle box = page != null ? page.getMediaBox() : null;
        double w = box != null ? box.getWidth() : 595.28;
        double h = box != null ? box.getHeight() : 841.89;

        // Booklet or 2-up sheet is landscape (two pages side by side)
        if (opts.isBookletPrint() || (opts.getPageHandlingMode() == PrintOptions.PageHandlingMode.MULTIPLE && opts.getPagesPerSheet() == PrintOptions.PagesPerSheet.TWO)) {
            return Math.max(w, h) / Math.min(w, h) * 1.0;
        }

        if (opts.getOrientation() == PrintOptions.OrientationMode.LANDSCAPE) {
            return Math.max(w, h) / Math.min(w, h);
        } else if (opts.getOrientation() == PrintOptions.OrientationMode.PORTRAIT) {
            return Math.min(w, h) / Math.max(w, h);
        }

        return w / Math.max(1.0, h);
    }

    private void updatePageInfo() {
        int pageIndex = Math.max(0, Math.min(pdDocument.getNumberOfPages() - 1, currentPage));
        List<Integer> pageIndices = printService.resolvePageIndices(opts, currentPage);
        if (previewSheetIndex < pageIndices.size()) {
            pageIndex = pageIndices.get(previewSheetIndex);
        }

        PDPage page = (pageIndex >= 0 && pageIndex < pdDocument.getNumberOfPages())
                ? pdDocument.getPage(pageIndex) : null;
        PDRectangle box = page != null ? page.getMediaBox() : null;
        double wPt = box != null ? box.getWidth() : 595.28;
        double hPt = box != null ? box.getHeight() : 841.89;
        double wMm = wPt * 25.4 / 72.0;
        double hMm = hPt * 25.4 / 72.0;

        String orientStr = (wPt > hPt) ? "Landscape" : "Portrait";
        String paperName = "A4";
        Printer p = opts.getPrinter();
        if (p != null && p.getPrinterAttributes() != null) {
            var papers = p.getPrinterAttributes().getSupportedPapers();
            if (papers != null && !papers.isEmpty()) {
                paperName = papers.iterator().next().getName();
            }
        }

        String info = String.format("Document: %.1f × %.1f mm (%s)  •  Paper: %s  •  Sheet %d of %d",
                wMm, hMm, orientStr, paperName, previewSheetIndex + 1, Math.max(1, totalPreviewSheets));
        pageInfoLabel.setText(info);
    }

    private void navigatePreview(int delta) {
        previewSheetIndex = Math.max(0, Math.min(totalPreviewSheets - 1, previewSheetIndex + delta));
        schedulePreviewRefresh();
    }

    // =========================================================================
    // Execute print
    // =========================================================================

    private void executePrint() {
        Printer printer = opts.getPrinter();
        if (printer == null) {
            showError("No printer selected", "Please select a printer before printing.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            showError("Could not create print job", "Failed to create a print job for the selected printer.");
            return;
        }

        List<Integer> pageIndices = printService.resolvePageIndices(opts, currentPage);
        if (pageIndices.isEmpty()) {
            showError("Empty page range", "No pages to print in the specified range.");
            return;
        }

        try {
            boolean success = printService.print(job, opts);
            if (success) {
                close();
            } else {
                showError("Print Failed", "The print job was cancelled or could not be completed.");
            }
        } catch (Exception ex) {
            showError("Print Error", "An error occurred while printing: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Snapshot helper for background rendering
    // =========================================================================

    private PrintOptions snapshotOpts() {
        PrintOptions s = new PrintOptions();
        s.setPageRangeMode(opts.getPageRangeMode());
        s.setCustomPageRange(opts.getCustomPageRange());
        s.setPrinter(opts.getPrinter());
        s.setCopies(opts.getCopies());
        s.setCollate(opts.isCollate());
        s.setReverseOrder(opts.isReverseOrder());
        s.setPageHandlingMode(opts.getPageHandlingMode());
        s.setOrientation(opts.getOrientation());
        s.setPagesPerSheet(opts.getPagesPerSheet());
        s.setNupHorizontalFirst(opts.isNupHorizontalFirst());
        s.setNupPageBorders(opts.isNupPageBorders());
        s.setDuplexMode(opts.getDuplexMode());
        s.setBookletPrint(opts.isBookletPrint());
        s.setBookletSubset(opts.getBookletSubset());
        s.setBookletFlipOnShortEdge(opts.isBookletFlipOnShortEdge());
        s.setFitMode(opts.getFitMode());
        s.setCustomScalePct(opts.getCustomScalePct());
        s.setMarginMode(opts.getMarginMode());
        s.setCustomMarginMm(opts.getCustomMarginMm());
        s.setAutoRotate(opts.isAutoRotate());
        s.setCenterOnPage(opts.isCenterOnPage());
        s.setPrintAnnotations(opts.isPrintAnnotations());
        s.setPrintFormFields(opts.isPrintFormFields());
        s.setPrintComments(opts.isPrintComments());
        s.setGrayscale(opts.isGrayscale());
        return s;
    }

    // =========================================================================
    // Summary label
    // =========================================================================

    private void updateSummaryLabel(Label label) {
        int total = pdDocument.getNumberOfPages();
        String rangeDesc = switch (opts.getPageRangeMode()) {
            case ALL     -> "All " + total + " pages";
            case CURRENT -> "Current page";
            case CUSTOM  -> "Custom range";
        };
        int copies = opts.getCopies();
        label.setText(rangeDesc + "  •  " + copies + " cop" + (copies == 1 ? "y" : "ies"));
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private VBox section(String title) {
        VBox sec = new VBox(6);
        sec.getStyleClass().add("print-section");
        sec.setPadding(new Insets(10, 16, 10, 16));

        Label lbl = new Label(title.toUpperCase());
        lbl.getStyleClass().add("print-section-title");
        sec.getChildren().add(lbl);
        return sec;
    }

    private Separator separator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("print-separator");
        sep.setPadding(new Insets(0));
        return sep;
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            a.setTitle(title + " – Oqteris");
            a.setHeaderText(title);
            a.initOwner(this);
            a.showAndWait();
        });
    }
}
