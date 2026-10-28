package org.oqteris.calc.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.TextFields;
import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import org.kordamp.ikonli.materialdesign2.*;
import org.oqteris.calc.formula.FunctionRegistry;
import org.oqteris.calc.model.*;
import org.oqteris.calc.model.Cell;
import org.oqteris.calc.render.SpreadsheetCanvas;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.ui.DocumentView;
import org.oqteris.ui.icon.AppIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main UI view for the Oqteris Calc spreadsheet editor.
 */
public class CalcDocumentView extends BorderPane implements DocumentView {
    private final Workbook workbook;
    private final SpreadsheetCanvas canvas = new SpreadsheetCanvas();

    // Scrollbars
    private final ScrollBar hScrollBar = new ScrollBar();
    private final ScrollBar vScrollBar = new ScrollBar();

    // Formula bar
    private final TextField coordField = new TextField("A1");
    private final TextField formulaField = TextFields.createClearableTextField();

    // Sheet tabs
    private final HBox sheetTabBar = new HBox(4);

    // Ribbon
    private final Ribbon ribbon = new Ribbon();
    private final Label statusStatsLabel = new Label("Ready");

    // Search Bar
    private final HBox searchBar = new HBox(8);
    private final TextField searchField = TextFields.createClearableTextField();
    private final Label searchResultLabel = new Label("");
    private final List<int[]> searchMatches = new ArrayList<>();
    private int currentMatchIndex = 0;

    // In-place cell editor
    private final TextField inPlaceEditor = new TextField();
    private final StackPane canvasContainer = new StackPane();

    public CalcDocumentView() {
        this(new Workbook());
    }

    public CalcDocumentView(Workbook workbook) {
        this.workbook = workbook;
        buildUI();
        loadSheet(workbook.getActiveWorksheet());
    }

    private void buildUI() {
        // 1. Top Formula Bar
        HBox formulaBar = createFormulaBar();

        // 2. Center Grid with Scrollbars and In-Place Editor
        canvasContainer.setAlignment(Pos.TOP_LEFT);
        canvasContainer.getChildren().addAll(canvas, inPlaceEditor);
        inPlaceEditor.setVisible(false);
        inPlaceEditor.setManaged(false);

        // Bind canvas dimensions to container
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        // Scrollbars setup
        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setMax(2000);
        hScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> canvas.setScrollOffsets(newVal.doubleValue(), vScrollBar.getValue()));

        vScrollBar.setOrientation(Orientation.VERTICAL);
        vScrollBar.setMin(0);
        vScrollBar.setMax(10000);
        vScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> canvas.setScrollOffsets(hScrollBar.getValue(), newVal.doubleValue()));

        canvas.setOnScrollOffsetsChanged((newX, newY) -> {
            hScrollBar.setValue(newX);
            vScrollBar.setValue(newY);
        });

        BorderPane gridWithScroll = new BorderPane();
        gridWithScroll.setCenter(canvasContainer);
        gridWithScroll.setRight(vScrollBar);
        gridWithScroll.setBottom(hScrollBar);

        // Canvas callbacks
        canvas.setFocusTraversable(true);
        canvas.setOnCellSelected((r, c) -> {
            hideInPlaceEditor(true);
            updateFormulaBar(r, c);
            updateStatusStats();
        });

        canvas.setOnCellDoubleClicked((r, c) -> {
            showInPlaceEditor(r, c);
        });

        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.F2) {
                showInPlaceEditor(canvas.getActiveRow(), canvas.getActiveCol());
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                canvas.setActiveCell(Math.max(0, canvas.getActiveRow() - 1), canvas.getActiveCol());
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                canvas.setActiveCell(canvas.getActiveRow() + 1, canvas.getActiveCol());
                e.consume();
            } else if (e.getCode() == KeyCode.LEFT) {
                canvas.setActiveCell(canvas.getActiveRow(), Math.max(0, canvas.getActiveCol() - 1));
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                canvas.setActiveCell(canvas.getActiveRow(), canvas.getActiveCol() + 1);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                Cell c = canvas.getWorksheet().getCell(canvas.getActiveRow(), canvas.getActiveCol());
                if (c != null) {
                    c.setValue("");
                    canvas.render();
                    updateFormulaBar(canvas.getActiveRow(), canvas.getActiveCol());
                }
                e.consume();
            }
        });

        canvas.setOnKeyTyped(e -> {
            String ch = e.getCharacter();
            if (ch != null && !ch.isEmpty() && !Character.isISOControl(ch.charAt(0))) {
                showInPlaceEditor(canvas.getActiveRow(), canvas.getActiveCol());
                inPlaceEditor.setText(ch);
                inPlaceEditor.positionCaret(1);
            }
        });

        // 3. Bottom Sheet Tabs
        sheetTabBar.setAlignment(Pos.CENTER_LEFT);
        sheetTabBar.setPadding(new Insets(3, 8, 3, 8));
        sheetTabBar.getStyleClass().add("sheet-tab-bar");
        refreshSheetTabs();

        // Search Bar setup
        setupSearchBar();

        VBox centerArea = new VBox(formulaBar, searchBar, gridWithScroll, sheetTabBar);
        VBox.setVgrow(gridWithScroll, Priority.ALWAYS);
        setCenter(centerArea);

        // 4. Ribbon Toolbar
        setupRibbonToolbar();
    }

    private HBox createFormulaBar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.getStyleClass().add("editor-formula-bar");

        coordField.setPrefWidth(65);
        coordField.setAlignment(Pos.CENTER);
        coordField.getStyleClass().add("coord-field");
        coordField.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");
        coordField.setOnAction(e -> {
            CellAddress addr = CellAddress.parse(coordField.getText().trim());
            if (addr != null) {
                canvas.setActiveCell(addr.getRow(), addr.getCol());
            }
        });

        Button fxBtn = new Button("fx");
        fxBtn.setStyle("-fx-font-weight: bold; -fx-font-style: italic; -fx-text-fill: #4f46e5; -fx-background-color: transparent;");
        fxBtn.setTooltip(new Tooltip("Insert Function"));
        fxBtn.setOnAction(e -> {
            if (!formulaField.getText().startsWith("=")) {
                formulaField.setText("=" + formulaField.getText());
            }
            formulaField.requestFocus();
            formulaField.positionCaret(formulaField.getText().length());
        });

        Button cancelBtn = AppIcons.iconButton(MaterialDesignC.CLOSE, "Cancel Edit", e -> {
            Cell c = canvas.getWorksheet().getCell(canvas.getActiveRow(), canvas.getActiveCol());
            formulaField.setText(c != null ? (c.getFormula() != null ? "=" + c.getFormula() : c.getStringValue()) : "");
        });
        cancelBtn.setStyle("-fx-background-color: transparent;");

        Button okBtn = AppIcons.iconButton(MaterialDesignC.CHECK, "Commit Formula", e -> commitFormula());
        okBtn.setStyle("-fx-background-color: transparent;");

        HBox.setHgrow(formulaField, Priority.ALWAYS);
        formulaField.setPromptText("Enter a formula or value...");
        formulaField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                commitFormula();
                canvas.setActiveCell(canvas.getActiveRow() + 1, canvas.getActiveCol());
            } else if (e.getCode() == KeyCode.ESCAPE) {
                cancelBtn.fire();
            }
        });

        bar.getChildren().addAll(coordField, fxBtn, okBtn, cancelBtn, formulaField);
        return bar;
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
        fontCombo.getItems().addAll("Calibri", "Segoe UI", "Arial", "Times New Roman", "Courier New");
        fontCombo.setValue("Calibri");
        fontCombo.setPrefWidth(120);
        fontCombo.setOnAction(e -> applyStyle(s -> s.setFontFamily(fontCombo.getValue())));

        ComboBox<Double> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll(9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 24.0);
        sizeCombo.setValue(11.0);
        sizeCombo.setPrefWidth(65);
        sizeCombo.setOnAction(e -> applyStyle(s -> s.setFontSize(sizeCombo.getValue())));

        ToggleButton boldBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_BOLD, "Bold (Ctrl+B)");
        boldBtn.setOnAction(e -> applyStyle(s -> s.setBold(boldBtn.isSelected())));

        ToggleButton italicBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_ITALIC, "Italic (Ctrl+I)");
        italicBtn.setOnAction(e -> applyStyle(s -> s.setItalic(italicBtn.isSelected())));

        ToggleButton underlineBtn = AppIcons.toggleButton(MaterialDesignF.FORMAT_UNDERLINE, "Underline (Ctrl+U)");
        underlineBtn.setOnAction(e -> applyStyle(s -> s.setUnderline(underlineBtn.isSelected())));

        ColorPicker textColorPicker = new ColorPicker(Color.BLACK);
        textColorPicker.setPrefWidth(44);
        textColorPicker.setTooltip(new Tooltip("Text Color"));
        textColorPicker.setOnAction(e -> applyStyle(s -> s.setTextColor(textColorPicker.getValue())));

        ColorPicker fillPicker = new ColorPicker(Color.TRANSPARENT);
        fillPicker.setPrefWidth(44);
        fillPicker.setTooltip(new Tooltip("Fill Color"));
        fillPicker.setOnAction(e -> applyStyle(s -> s.setFillColor(fillPicker.getValue())));

        fontGroup.getNodes().addAll(fontCombo, sizeCombo, boldBtn, italicBtn, underlineBtn, textColorPicker, fillPicker);

        // Group: Alignment
        RibbonGroup alignGroup = new RibbonGroup();
        alignGroup.setTitle("Alignment");

        ToggleButton alignLeft = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_LEFT, "Align Left");
        alignLeft.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.LEFT)));

        ToggleButton alignCenter = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_CENTER, "Center");
        alignCenter.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.CENTER)));

        ToggleButton alignRight = AppIcons.toggleButton(MaterialDesignF.FORMAT_ALIGN_RIGHT, "Align Right");
        alignRight.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.RIGHT)));

        SegmentedButton alignSegmented = new SegmentedButton(alignLeft, alignCenter, alignRight);

        Button mergeCenterBtn = AppIcons.button("Merge & Center", MaterialDesignT.TABLE_MERGE_CELLS, e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null && (range.getRowCount() > 1 || range.getColumnCount() > 1)) {
                Worksheet ws = canvas.getWorksheet();
                ws.addMergedRange(range);
                applyStyle(s -> s.setAlignment(CellStyle.Alignment.CENTER));
                canvas.render();
            }
        });

        alignGroup.getNodes().addAll(alignSegmented, mergeCenterBtn);

        // Group: Number
        RibbonGroup numGroup = new RibbonGroup();
        numGroup.setTitle("Number");

        ComboBox<String> numFormatCombo = new ComboBox<>();
        numFormatCombo.getItems().addAll("General", "0.00", "$#,##0.00", "0.00%", "yyyy-mm-dd");
        numFormatCombo.setValue("General");
        numFormatCombo.setPrefWidth(105);
        numFormatCombo.setOnAction(e -> applyStyle(s -> s.setNumberFormat(numFormatCombo.getValue())));

        numGroup.getNodes().add(numFormatCombo);

        // Group: Editing
        RibbonGroup editGroup = new RibbonGroup();
        editGroup.setTitle("Editing");

        Button autoSumBtn = AppIcons.button("AutoSum", MaterialDesignS.SIGMA, e -> {
            int r = canvas.getActiveRow();
            int c = canvas.getActiveCol();
            if (r > 0) {
                String formula = "=SUM(" + CellAddress.colToLetters(c) + "1:" + CellAddress.colToLetters(c) + r + ")";
                formulaField.setText(formula);
                commitFormula();
            }
        });

        Button sortAsc = AppIcons.button("Sort A-Z", MaterialDesignS.SORT_ALPHABETICAL_ASCENDING, e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null) {
                canvas.getWorksheet().sortRange(range, canvas.getActiveCol(), true);
                workbook.recalculate();
                canvas.render();
            }
        });

        Button sortDesc = AppIcons.button("Sort Z-A", MaterialDesignS.SORT_ALPHABETICAL_DESCENDING, e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null) {
                canvas.getWorksheet().sortRange(range, canvas.getActiveCol(), false);
                workbook.recalculate();
                canvas.render();
            }
        });

        editGroup.getNodes().addAll(autoSumBtn, sortAsc, sortDesc);

        homeTab.getRibbonGroups().addAll(clipboardGroup, fontGroup, alignGroup, numGroup, editGroup);

        // =========================================================================
        // Tab 2: INSERT
        // =========================================================================
        RibbonTab insertTab = new RibbonTab("Insert");

        RibbonGroup chartsGroup = new RibbonGroup();
        chartsGroup.setTitle("Charts");
        Button insertChartBtn = AppIcons.button("Insert Chart...", MaterialDesignC.CHART_BAR, e -> {
            Stage stage = getScene() != null ? (Stage) getScene().getWindow() : null;
            new SpreadsheetChartDialog(stage, canvas.getWorksheet(), canvas.getSelectedRange()).show();
        });
        chartsGroup.getNodes().add(insertChartBtn);

        insertTab.getRibbonGroups().add(chartsGroup);

        // =========================================================================
        // Tab 3: FORMULAS & DATA
        // =========================================================================
        RibbonTab formulasTab = new RibbonTab("Formulas & Data");

        RibbonGroup funcsGroup = new RibbonGroup();
        funcsGroup.setTitle("Function Library");

        MenuButton insertFuncMenu = new MenuButton("Common Formulas");
        insertFuncMenu.setGraphic(AppIcons.of(MaterialDesignF.FUNCTION, 16));
        String[] funcs = {"SUM", "AVERAGE", "COUNT", "MAX", "MIN", "IF", "VLOOKUP"};
        for (String f : funcs) {
            MenuItem mi = new MenuItem(f + "()");
            mi.setOnAction(e -> {
                formulaField.setText("=" + f + "()");
                formulaField.requestFocus();
                formulaField.positionCaret(f.length() + 2);
            });
            insertFuncMenu.getItems().add(mi);
        }

        Button recalcBtn = AppIcons.button("Recalculate Now", MaterialDesignR.REFRESH, e -> {
            workbook.recalculate();
            canvas.render();
            updateStatusStats();
        });

        funcsGroup.getNodes().addAll(insertFuncMenu, recalcBtn);
        formulasTab.getRibbonGroups().add(funcsGroup);

        // =========================================================================
        // Tab 4: VIEW
        // =========================================================================
        RibbonTab viewTab = new RibbonTab("View");

        RibbonGroup findGroup = new RibbonGroup();
        findGroup.setTitle("Search");
        Button findBtn = AppIcons.button("Find in Sheet", MaterialDesignM.MAGNIFY, e -> toggleSearch(!searchBar.isVisible()));
        findGroup.getNodes().add(findBtn);

        RibbonGroup zoomGroup = new RibbonGroup();
        zoomGroup.setTitle("Zoom");
        Button zoomInBtn = AppIcons.iconButton(MaterialDesignM.MAGNIFY_PLUS_OUTLINE, "Zoom In", e -> setZoom(canvas.getZoom() + 0.15));
        Button zoomResetBtn = new Button("100%");
        zoomResetBtn.setOnAction(e -> setZoom(1.0));
        Button zoomOutBtn = AppIcons.iconButton(MaterialDesignM.MAGNIFY_MINUS_OUTLINE, "Zoom Out", e -> setZoom(canvas.getZoom() - 0.15));
        zoomGroup.getNodes().addAll(zoomOutBtn, zoomResetBtn, zoomInBtn);

        viewTab.getRibbonGroups().addAll(findGroup, zoomGroup);

        ribbon.getTabs().addAll(homeTab, insertTab, formulasTab, viewTab);
    }

    private void applyStyle(java.util.function.Consumer<CellStyle> modifier) {
        CellRangeAddress range = canvas.getSelectedRange();
        if (range == null) return;
        Worksheet ws = canvas.getWorksheet();

        for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
            for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                Cell cell = ws.getOrCreateCell(r, c);
                CellStyle s = cell.getStyle().copy();
                modifier.accept(s);
                cell.setStyle(s);
            }
        }
        workbook.setModified(true);
        canvas.render();
    }

    public void loadSheet(Worksheet sheet) {
        if (sheet == null) return;
        canvas.setWorksheet(sheet);
        updateFormulaBar(canvas.getActiveRow(), canvas.getActiveCol());
        updateStatusStats();
    }

    private void updateFormulaBar(int row, int col) {
        coordField.setText(CellAddress.colToLetters(col) + (row + 1));
        Cell cell = canvas.getWorksheet().getCell(row, col);
        if (cell != null) {
            if (cell.getType() == CellType.FORMULA && cell.getFormula() != null) {
                formulaField.setText("=" + cell.getFormula());
            } else {
                formulaField.setText(cell.getStringValue());
            }
        } else {
            formulaField.setText("");
        }
    }

    private void commitFormula() {
        int r = canvas.getActiveRow();
        int c = canvas.getActiveCol();
        Worksheet ws = canvas.getWorksheet();
        Cell cell = ws.getOrCreateCell(r, c);

        String text = formulaField.getText().trim();
        if (text.startsWith("=")) {
            cell.setFormula(text);
        } else {
            cell.setValue(text);
        }

        // Recalculate
        workbook.getCalculationEngine().evaluateCell(workbook, ws, cell);
        workbook.getCalculationEngine().notifyCellChanged(workbook, ws, new CellAddress(ws.getName(), r, c));
        workbook.setModified(true);

        canvas.render();
        updateStatusStats();
    }

    private void updateStatusStats() {
        CellRangeAddress range = canvas.getSelectedRange();
        if (range == null) {
            statusStatsLabel.setText("Ready");
            return;
        }

        Worksheet ws = canvas.getWorksheet();
        int count = 0;
        double sum = 0;
        int numCount = 0;

        for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
            for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                Cell cell = ws.getCell(r, c);
                if (cell != null && cell.getType() != CellType.BLANK) {
                    count++;
                    if (cell.isNumeric()) {
                        sum += cell.getNumericValue();
                        numCount++;
                    }
                }
            }
        }

        if (numCount > 0) {
            double avg = sum / numCount;
            statusStatsLabel.setText(String.format("Count: %d | Sum: %.2f | Average: %.2f", count, sum, avg));
        } else if (count > 0) {
            statusStatsLabel.setText("Count: " + count);
        } else {
            statusStatsLabel.setText("Ready");
        }
    }

    private void refreshSheetTabs() {
        sheetTabBar.getChildren().clear();
        for (int i = 0; i < workbook.getSheetCount(); i++) {
            final int index = i;
            Worksheet ws = workbook.getSheet(index);
            Button tabBtn = new Button(ws.getName());
            tabBtn.getStyleClass().add("sheet-tab-button");

            if (index == workbook.getActiveSheetIndex()) {
                tabBtn.getStyleClass().add("sheet-tab-button-active");
            }

            tabBtn.setOnAction(e -> {
                workbook.setActiveSheetIndex(index);
                loadSheet(ws);
                refreshSheetTabs();
            });

            // Context menu for sheet tabs
            ContextMenu menu = new ContextMenu();
            MenuItem renameItem = new MenuItem("Rename Sheet...");
            renameItem.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog(ws.getName());
                dialog.setTitle("Rename Sheet");
                dialog.setHeaderText("Enter new name for worksheet:");
                dialog.showAndWait().ifPresent(newName -> {
                    if (!newName.isBlank()) {
                        ws.setName(newName.trim());
                        refreshSheetTabs();
                        workbook.setModified(true);
                    }
                });
            });

            MenuItem deleteItem = new MenuItem("Delete Sheet");
            deleteItem.setOnAction(e -> {
                if (workbook.getSheetCount() > 1) {
                    workbook.removeSheet(index);
                    loadSheet(workbook.getActiveWorksheet());
                    refreshSheetTabs();
                }
            });
            if (workbook.getSheetCount() <= 1) deleteItem.setDisable(true);

            menu.getItems().addAll(renameItem, deleteItem);
            tabBtn.setContextMenu(menu);

            sheetTabBar.getChildren().add(tabBtn);
        }

        Button addSheetBtn = new Button("+");
        addSheetBtn.setStyle("-fx-font-weight: bold; -fx-background-color: transparent;");
        addSheetBtn.setOnAction(e -> {
            workbook.createSheet("Sheet" + (workbook.getSheetCount() + 1));
            refreshSheetTabs();
        });
        sheetTabBar.getChildren().add(addSheetBtn);
    }

    private void showInPlaceEditor(int r, int c) {
        double[] bounds = canvas.getCellBounds(r, c);
        if (bounds == null) return;

        Cell cell = canvas.getWorksheet().getCell(r, c);
        inPlaceEditor.setText(cell != null ? (cell.getFormula() != null ? "=" + cell.getFormula() : cell.getStringValue()) : "");
        inPlaceEditor.setTranslateX(bounds[0]);
        inPlaceEditor.setTranslateY(bounds[1]);
        inPlaceEditor.setPrefWidth(Math.max(bounds[2] + 4, 80));
        inPlaceEditor.setPrefHeight(Math.max(bounds[3], 24));
        inPlaceEditor.setStyle("-fx-border-color: #2563eb; -fx-border-width: 2px; -fx-background-color: white; -fx-padding: 1 4 1 4; -fx-font-family: 'Calibri', 'Segoe UI';");

        inPlaceEditor.setVisible(true);
        inPlaceEditor.setManaged(true);
        inPlaceEditor.requestFocus();
        inPlaceEditor.selectAll();

        inPlaceEditor.setOnAction(e -> {
            formulaField.setText(inPlaceEditor.getText());
            commitFormula();
            hideInPlaceEditor(true);
            canvas.setActiveCell(canvas.getActiveRow() + 1, canvas.getActiveCol());
        });
        inPlaceEditor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hideInPlaceEditor(false);
            } else if (e.getCode() == KeyCode.TAB) {
                formulaField.setText(inPlaceEditor.getText());
                commitFormula();
                hideInPlaceEditor(true);
                canvas.setActiveCell(canvas.getActiveRow(), canvas.getActiveCol() + 1);
                e.consume();
            }
        });
    }

    private void hideInPlaceEditor(boolean commit) {
        if (inPlaceEditor.isVisible()) {
            if (commit) {
                formulaField.setText(inPlaceEditor.getText());
                commitFormula();
            }
            inPlaceEditor.setVisible(false);
            inPlaceEditor.setManaged(false);
            canvas.requestFocus();
        }
    }

    private void setupSearchBar() {
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5, 12, 5, 12));
        searchBar.getStyleClass().add("editor-search-bar");
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField.setPromptText("Find text or numbers in worksheet...");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> performSearch(searchField.getText(), true));

        Button prevBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_LEFT, "Previous Match", e -> navigateSearch(-1));
        Button nextBtn = AppIcons.iconButton(MaterialDesignC.CHEVRON_RIGHT, "Next Match", e -> navigateSearch(1));
        Button closeBtn = AppIcons.iconButton(MaterialDesignC.CLOSE, "Close Search", e -> toggleSearch(false));
        closeBtn.setStyle("-fx-background-color: transparent;");

        searchResultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 500;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchBar.getChildren().addAll(
                new Label("Find:"), searchField, prevBtn, nextBtn,
                searchResultLabel, spacer, closeBtn
        );
    }

    private void toggleSearch(boolean visible) {
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible) {
            searchField.requestFocus();
        } else {
            searchMatches.clear();
            searchResultLabel.setText("");
        }
    }

    private void performSearch(String query, boolean jumpToFirst) {
        searchMatches.clear();
        if (query == null || query.isBlank() || canvas.getWorksheet() == null) {
            searchResultLabel.setText("");
            return;
        }

        Worksheet ws = canvas.getWorksheet();
        String lowerQuery = query.toLowerCase();

        for (int r = 0; r <= ws.getMaxRow(); r++) {
            Row row = ws.getRow(r);
            if (row != null) {
                for (int c = 0; c <= ws.getMaxCol(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        String str = cell.getStringValue().toLowerCase();
                        String formula = cell.getFormula() != null ? cell.getFormula().toLowerCase() : "";
                        if (str.contains(lowerQuery) || formula.contains(lowerQuery)) {
                            searchMatches.add(new int[]{r, c});
                        }
                    }
                }
            }
        }

        if (!searchMatches.isEmpty()) {
            currentMatchIndex = 0;
            searchResultLabel.setText("Match 1 of " + searchMatches.size());
            if (jumpToFirst) {
                int[] m = searchMatches.get(0);
                canvas.setActiveCell(m[0], m[1]);
            }
        } else {
            searchResultLabel.setText("No matches");
        }
    }

    private void navigateSearch(int direction) {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + direction + searchMatches.size()) % searchMatches.size();
        searchResultLabel.setText("Match " + (currentMatchIndex + 1) + " of " + searchMatches.size());
        int[] m = searchMatches.get(currentMatchIndex);
        canvas.setActiveCell(m[0], m[1]);
    }

    @Override
    public DocumentModel getDocument() {
        return workbook;
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
        return canvas.getZoom();
    }

    @Override
    public void setZoom(double zoom) {
        canvas.setZoom(zoom);
    }

    @Override
    public void copy() {
        CellRangeAddress range = canvas.getSelectedRange();
        if (range == null || canvas.getWorksheet() == null) return;

        Worksheet ws = canvas.getWorksheet();
        StringBuilder tsv = new StringBuilder();

        for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
            for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                Cell cell = ws.getCell(r, c);
                if (cell != null) {
                    if (cell.getFormula() != null) {
                        tsv.append("=").append(cell.getFormula());
                    } else {
                        tsv.append(cell.getStringValue());
                    }
                }
                if (c < range.getLastCol()) tsv.append("\t");
            }
            if (r < range.getLastRow()) tsv.append("\n");
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(tsv.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void cut() {
        copy();
        CellRangeAddress range = canvas.getSelectedRange();
        if (range != null && canvas.getWorksheet() != null) {
            Worksheet ws = canvas.getWorksheet();
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                    Cell cell = ws.getCell(r, c);
                    if (cell != null) {
                        cell.setValue(null);
                        cell.setFormula(null);
                    }
                }
            }
            workbook.recalculate();
            canvas.render();
            workbook.setModified(true);
        }
    }

    @Override
    public void paste() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (!cb.hasString() || canvas.getWorksheet() == null) return;

        String text = cb.getString();
        Worksheet ws = canvas.getWorksheet();
        int baseR = canvas.getActiveRow();
        int baseC = canvas.getActiveCol();

        String[] lines = text.split("\\r?\\n");
        for (int r = 0; r < lines.length; r++) {
            String[] tokens = lines[r].split("\t", -1);
            for (int c = 0; c < tokens.length; c++) {
                String val = tokens[c];
                Cell cell = ws.getOrCreateCell(baseR + r, baseC + c);
                if (val.startsWith("=")) {
                    cell.setFormula(val.substring(1));
                } else {
                    try {
                        cell.setValue(Double.parseDouble(val));
                    } catch (Exception ex) {
                        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                            cell.setValue(Boolean.parseBoolean(val));
                        } else {
                            cell.setValue(val);
                        }
                    }
                }
            }
        }

        workbook.recalculate();
        canvas.render();
        workbook.setModified(true);
    }

    @Override
    public void selectAll() {
        Worksheet ws = canvas.getWorksheet();
        if (ws != null) {
            canvas.setSelectedRange(new CellRangeAddress(0, Math.max(10, ws.getMaxRow()), 0, Math.max(10, ws.getMaxCol())));
        }
    }

    @Override
    public void showSearch() {
        toggleSearch(true);
    }

    @Override
    public void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene() != null ? getScene().getWindow() : null)) {
            if (job.printPage(canvas)) {
                job.endJob();
            }
        }
    }

    @Override
    public void close() {
    }
}
