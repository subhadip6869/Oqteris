package org.oqteris.calc.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.oqteris.calc.model.Cell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.oqteris.calc.formula.FunctionRegistry;
import org.oqteris.calc.model.*;
import org.oqteris.calc.render.SpreadsheetCanvas;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.ui.DocumentView;

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
    private final TextField formulaField = new TextField();

    // Sheet tabs
    private final HBox sheetTabBar = new HBox(4);

    // Contextual ribbon and status
    private final HBox ribbonBar = new HBox(6);
    private final Label statusStatsLabel = new Label("Ready");

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

        BorderPane gridWithScroll = new BorderPane();
        gridWithScroll.setCenter(canvasContainer);
        gridWithScroll.setRight(vScrollBar);
        gridWithScroll.setBottom(hScrollBar);

        // Canvas callbacks
        canvas.setOnCellSelected((r, c) -> {
            hideInPlaceEditor(true);
            updateFormulaBar(r, c);
            updateStatusStats();
        });

        canvas.setOnCellDoubleClicked((r, c) -> {
            showInPlaceEditor(r, c);
        });

        // 3. Bottom Sheet Tabs
        sheetTabBar.setAlignment(Pos.CENTER_LEFT);
        sheetTabBar.setPadding(new Insets(3, 8, 3, 8));
        sheetTabBar.setStyle("-fx-background-color: #f3f3f3; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        refreshSheetTabs();

        VBox centerArea = new VBox(formulaBar, gridWithScroll, sheetTabBar);
        VBox.setVgrow(gridWithScroll, Priority.ALWAYS);
        setCenter(centerArea);

        // 4. Ribbon Toolbar
        setupRibbonToolbar();
    }

    private HBox createFormulaBar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        bar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        coordField.setPrefWidth(55);
        coordField.setAlignment(Pos.CENTER);
        coordField.setOnAction(e -> {
            CellAddress addr = CellAddress.parse(coordField.getText().trim());
            if (addr != null) {
                canvas.setActiveCell(addr.getRow(), addr.getCol());
            }
        });

        Button cancelBtn = new Button("✕");
        cancelBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc3545;");
        cancelBtn.setOnAction(e -> {
            Cell c = canvas.getWorksheet().getCell(canvas.getActiveRow(), canvas.getActiveCol());
            formulaField.setText(c != null ? (c.getFormula() != null ? "=" + c.getFormula() : c.getStringValue()) : "");
        });

        Button okBtn = new Button("✓");
        okBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #28a745;");
        okBtn.setOnAction(e -> commitFormula());

        Button fxBtn = new Button("fx");
        fxBtn.setStyle("-fx-font-weight: bold; -fx-font-style: italic; -fx-text-fill: #0078d4;");
        fxBtn.setOnAction(e -> {
            if (!formulaField.getText().startsWith("=")) {
                formulaField.setText("=" + formulaField.getText());
            }
            formulaField.requestFocus();
            formulaField.positionCaret(formulaField.getText().length());
        });

        HBox.setHgrow(formulaField, Priority.ALWAYS);
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
        ribbonBar.setAlignment(Pos.CENTER_LEFT);
        ribbonBar.setSpacing(6);

        // Font family
        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("Segoe UI", "Calibri", "Arial", "Times New Roman", "Courier New");
        fontCombo.setValue("Segoe UI");
        fontCombo.setPrefWidth(120);
        fontCombo.setOnAction(e -> applyStyle(s -> s.setFontFamily(fontCombo.getValue())));

        // Font size
        ComboBox<Double> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll(9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 24.0);
        sizeCombo.setValue(11.0);
        sizeCombo.setPrefWidth(65);
        sizeCombo.setOnAction(e -> applyStyle(s -> s.setFontSize(sizeCombo.getValue())));

        // Bold / Italic / Underline
        ToggleButton boldBtn = new ToggleButton("B");
        boldBtn.setStyle("-fx-font-weight: bold;");
        boldBtn.setOnAction(e -> applyStyle(s -> s.setBold(boldBtn.isSelected())));

        ToggleButton italicBtn = new ToggleButton("I");
        italicBtn.setStyle("-fx-font-style: italic;");
        italicBtn.setOnAction(e -> applyStyle(s -> s.setItalic(italicBtn.isSelected())));

        ToggleButton underlineBtn = new ToggleButton("U");
        underlineBtn.setOnAction(e -> applyStyle(s -> s.setUnderline(underlineBtn.isSelected())));

        // ColorPickers
        ColorPicker textColorPicker = new ColorPicker(Color.BLACK);
        textColorPicker.setPrefWidth(40);
        textColorPicker.setOnAction(e -> applyStyle(s -> s.setTextColor(textColorPicker.getValue())));

        ColorPicker fillPicker = new ColorPicker(Color.TRANSPARENT);
        fillPicker.setPrefWidth(40);
        fillPicker.setOnAction(e -> applyStyle(s -> s.setFillColor(fillPicker.getValue())));

        // Alignments
        Button alignLeft = new Button("Left");
        alignLeft.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.LEFT)));

        Button alignCenter = new Button("Center");
        alignCenter.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.CENTER)));

        Button alignRight = new Button("Right");
        alignRight.setOnAction(e -> applyStyle(s -> s.setAlignment(CellStyle.Alignment.RIGHT)));

        Button mergeCenterBtn = new Button("Merge & Center");
        mergeCenterBtn.setOnAction(e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null && (range.getRowCount() > 1 || range.getColumnCount() > 1)) {
                Worksheet ws = canvas.getWorksheet();
                ws.addMergedRange(range);
                applyStyle(s -> s.setAlignment(CellStyle.Alignment.CENTER));
                canvas.render();
            }
        });

        // Number Formats
        ComboBox<String> numFormatCombo = new ComboBox<>();
        numFormatCombo.getItems().addAll("General", "0.00", "$#,##0.00", "0.00%", "yyyy-mm-dd");
        numFormatCombo.setValue("General");
        numFormatCombo.setPrefWidth(100);
        numFormatCombo.setOnAction(e -> applyStyle(s -> s.setNumberFormat(numFormatCombo.getValue())));

        // Operations: Sort & Auto-sum
        Button sortAsc = new Button("Sort A-Z");
        sortAsc.setOnAction(e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null) {
                canvas.getWorksheet().sortRange(range, canvas.getActiveCol(), true);
                workbook.recalculate();
                canvas.render();
            }
        });

        Button sortDesc = new Button("Sort Z-A");
        sortDesc.setOnAction(e -> {
            CellRangeAddress range = canvas.getSelectedRange();
            if (range != null) {
                canvas.getWorksheet().sortRange(range, canvas.getActiveCol(), false);
                workbook.recalculate();
                canvas.render();
            }
        });

        Button autoSumBtn = new Button("Σ AutoSum");
        autoSumBtn.setOnAction(e -> {
            int r = canvas.getActiveRow();
            int c = canvas.getActiveCol();
            if (r > 0) {
                String formula = "=SUM(" + CellAddress.colToLetters(c) + "1:" + CellAddress.colToLetters(c) + r + ")";
                formulaField.setText(formula);
                commitFormula();
            }
        });

        ribbonBar.getChildren().addAll(
                fontCombo, sizeCombo, boldBtn, italicBtn, underlineBtn,
                textColorPicker, fillPicker, new Separator(Orientation.VERTICAL),
                alignLeft, alignCenter, alignRight, mergeCenterBtn, new Separator(Orientation.VERTICAL),
                numFormatCombo, new Separator(Orientation.VERTICAL),
                sortAsc, sortDesc, autoSumBtn
        );
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

            if (index == workbook.getActiveSheetIndex()) {
                tabBtn.setStyle("-fx-background-color: white; -fx-font-weight: bold; -fx-border-color: #0078d4; -fx-border-width: 0 0 2 0;");
            } else {
                tabBtn.setStyle("-fx-background-color: transparent;");
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
        Cell cell = canvas.getWorksheet().getCell(r, c);
        inPlaceEditor.setText(cell != null ? (cell.getFormula() != null ? "=" + cell.getFormula() : cell.getStringValue()) : "");
        inPlaceEditor.setVisible(true);
        inPlaceEditor.setManaged(true);
        inPlaceEditor.requestFocus();
        inPlaceEditor.selectAll();

        inPlaceEditor.setOnAction(e -> {
            formulaField.setText(inPlaceEditor.getText());
            commitFormula();
            hideInPlaceEditor(true);
        });
        inPlaceEditor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hideInPlaceEditor(false);
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

    @Override
    public DocumentModel getDocument() {
        return workbook;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbonBar;
    }

    @Override
    public Node getStatusBarNode() {
        return statusStatsLabel;
    }

    @Override
    public void close() {
    }
}
