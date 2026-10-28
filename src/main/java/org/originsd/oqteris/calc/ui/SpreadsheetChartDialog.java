package org.originsd.oqteris.calc.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.originsd.oqteris.calc.chart.SpreadsheetChart;
import org.originsd.oqteris.calc.model.Cell;
import org.originsd.oqteris.calc.model.CellRangeAddress;
import org.originsd.oqteris.calc.model.Worksheet;
import org.originsd.oqteris.ui.ThemeManager;

/**
 * Modern interactive chart creation and visualization dialog for Oqteris Calc.
 */
public class SpreadsheetChartDialog extends Stage {
    private final Worksheet worksheet;
    private final StackPane chartPreviewContainer = new StackPane();
    private final TextField titleField = new TextField("Sales & Budget Summary");
    private final ComboBox<SpreadsheetChart.ChartType> typeCombo = new ComboBox<>();
    private final TextField rangeField = new TextField();

    public SpreadsheetChartDialog(Stage owner, Worksheet worksheet, CellRangeAddress initialRange) {
        this.worksheet = worksheet;
        initOwner(owner);
        initModality(Modality.NONE);
        setTitle("Chart Designer - Oqteris Calc");
        setResizable(true);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setPrefSize(720, 520);

        // Header controls
        HBox configBar = new HBox(10);
        configBar.setAlignment(Pos.CENTER_LEFT);

        typeCombo.getItems().addAll(SpreadsheetChart.ChartType.values());
        typeCombo.setValue(SpreadsheetChart.ChartType.COLUMN);
        typeCombo.setOnAction(e -> updateChartPreview());

        String defaultRange = initialRange != null ? initialRange.toString() : "A1:D10";
        rangeField.setText(defaultRange);
        rangeField.setPrefWidth(120);
        rangeField.setOnAction(e -> updateChartPreview());

        titleField.setPrefWidth(200);
        titleField.textProperty().addListener((obs, o, n) -> updateChartPreview());

        Button refreshBtn = new Button("Refresh Preview");
        refreshBtn.setOnAction(e -> updateChartPreview());

        configBar.getChildren().addAll(
                new Label("Type:"), typeCombo,
                new Label("Title:"), titleField,
                new Label("Range:"), rangeField,
                refreshBtn
        );

        // Preview container
        chartPreviewContainer.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        chartPreviewContainer.setPadding(new Insets(16));
        VBox.setVgrow(chartPreviewContainer, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> close());
        HBox bottomBar = new HBox(closeBtn);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(configBar, chartPreviewContainer, bottomBar);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);

        updateChartPreview();
    }

    private void updateChartPreview() {
        chartPreviewContainer.getChildren().clear();
        CellRangeAddress range = CellRangeAddress.parse(rangeField.getText().trim());
        if (range == null || worksheet == null) {
            chartPreviewContainer.getChildren().add(new Label("Invalid cell range specified."));
            return;
        }

        String title = titleField.getText().trim();
        SpreadsheetChart.ChartType type = typeCombo.getValue();

        try {
            if (type == SpreadsheetChart.ChartType.PIE) {
                ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
                int labelCol = range.getFirstCol();
                int valueCol = Math.min(range.getLastCol(), labelCol + 1);

                for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                    Cell labelCell = worksheet.getCell(r, labelCol);
                    Cell valCell = worksheet.getCell(r, valueCol);
                    String label = labelCell != null ? labelCell.getStringValue() : "Row " + (r + 1);
                    double val = valCell != null ? valCell.getNumericValue() : 0.0;
                    if (val > 0) {
                        pieData.add(new PieChart.Data(label, val));
                    }
                }
                PieChart pieChart = new PieChart(pieData);
                pieChart.setTitle(title);
                chartPreviewContainer.getChildren().add(pieChart);

            } else if (type == SpreadsheetChart.ChartType.BAR) {
                NumberAxis xAxis = new NumberAxis();
                CategoryAxis yAxis = new CategoryAxis();
                BarChart<Number, String> barChart = new BarChart<>(xAxis, yAxis);
                barChart.setTitle(title);

                for (int c = range.getFirstCol() + 1; c <= range.getLastCol(); c++) {
                    Cell headerCell = worksheet.getCell(range.getFirstRow(), c);
                    String seriesName = headerCell != null ? headerCell.getStringValue() : "Series " + c;
                    XYChart.Series<Number, String> series = new XYChart.Series<>();
                    series.setName(seriesName);

                    for (int r = range.getFirstRow() + 1; r <= range.getLastRow(); r++) {
                        Cell catCell = worksheet.getCell(r, range.getFirstCol());
                        Cell valCell = worksheet.getCell(r, c);
                        String cat = catCell != null ? catCell.getStringValue() : "Row " + r;
                        double val = valCell != null ? valCell.getNumericValue() : 0.0;
                        series.getData().add(new XYChart.Data<>(val, cat));
                    }
                    barChart.getData().add(series);
                }
                chartPreviewContainer.getChildren().add(barChart);

            } else if (type == SpreadsheetChart.ChartType.LINE) {
                CategoryAxis xAxis = new CategoryAxis();
                NumberAxis yAxis = new NumberAxis();
                LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
                lineChart.setTitle(title);

                for (int c = range.getFirstCol() + 1; c <= range.getLastCol(); c++) {
                    Cell headerCell = worksheet.getCell(range.getFirstRow(), c);
                    String seriesName = headerCell != null ? headerCell.getStringValue() : "Series " + c;
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName(seriesName);

                    for (int r = range.getFirstRow() + 1; r <= range.getLastRow(); r++) {
                        Cell catCell = worksheet.getCell(r, range.getFirstCol());
                        Cell valCell = worksheet.getCell(r, c);
                        String cat = catCell != null ? catCell.getStringValue() : "Row " + r;
                        double val = valCell != null ? valCell.getNumericValue() : 0.0;
                        series.getData().add(new XYChart.Data<>(cat, val));
                    }
                    lineChart.getData().add(series);
                }
                chartPreviewContainer.getChildren().add(lineChart);

            } else {
                // COLUMN (Default)
                CategoryAxis xAxis = new CategoryAxis();
                NumberAxis yAxis = new NumberAxis();
                BarChart<String, Number> colChart = new BarChart<>(xAxis, yAxis);
                colChart.setTitle(title);

                int startR = range.getFirstRow();
                int startC = range.getFirstCol();
                boolean hasHeaders = range.getRowCount() > 1 && range.getColumnCount() > 1;

                int firstDataRow = hasHeaders ? startR + 1 : startR;
                int firstDataCol = hasHeaders ? startC + 1 : startC;

                for (int c = firstDataCol; c <= range.getLastCol(); c++) {
                    Cell headerCell = worksheet.getCell(startR, c);
                    String seriesName = (hasHeaders && headerCell != null) ? headerCell.getStringValue() : "Series " + (c - startC + 1);
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName(seriesName);

                    for (int r = firstDataRow; r <= range.getLastRow(); r++) {
                        Cell catCell = worksheet.getCell(r, startC);
                        Cell valCell = worksheet.getCell(r, c);
                        String cat = (hasHeaders && catCell != null) ? catCell.getStringValue() : "Item " + (r - startR + 1);
                        double val = valCell != null ? valCell.getNumericValue() : 0.0;
                        series.getData().add(new XYChart.Data<>(cat, val));
                    }
                    colChart.getData().add(series);
                }
                chartPreviewContainer.getChildren().add(colChart);
            }
        } catch (Exception ex) {
            chartPreviewContainer.getChildren().add(new Label("Could not render chart: " + ex.getMessage()));
        }
    }
}
