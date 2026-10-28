package org.originsd.oqteris.calc.chart;

import org.originsd.oqteris.calc.model.CellRangeAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Model describing an embedded chart linked to cell ranges in a worksheet.
 */
public class SpreadsheetChart {
    public enum ChartType {
        COLUMN,
        BAR,
        LINE,
        AREA,
        PIE
    }

    private ChartType chartType = ChartType.COLUMN;
    private String title = "Chart";
    private CellRangeAddress categoryRange;
    private final List<CellRangeAddress> seriesRanges = new ArrayList<>();
    private double x = 50;
    private double y = 50;
    private double width = 450;
    private double height = 300;

    public SpreadsheetChart() {
    }

    public SpreadsheetChart(ChartType chartType, String title, CellRangeAddress categoryRange, List<CellRangeAddress> seriesRanges) {
        this.chartType = chartType;
        this.title = title;
        this.categoryRange = categoryRange;
        if (seriesRanges != null) {
            this.seriesRanges.addAll(seriesRanges);
        }
    }

    public ChartType getChartType() {
        return chartType;
    }

    public void setChartType(ChartType chartType) {
        this.chartType = chartType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public CellRangeAddress getCategoryRange() {
        return categoryRange;
    }

    public void setCategoryRange(CellRangeAddress categoryRange) {
        this.categoryRange = categoryRange;
    }

    public List<CellRangeAddress> getSeriesRanges() {
        return seriesRanges;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}
