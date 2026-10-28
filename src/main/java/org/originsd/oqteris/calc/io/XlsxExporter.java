package org.originsd.oqteris.calc.io;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.originsd.oqteris.calc.model.*;
import org.originsd.oqteris.calc.model.Cell;
import org.originsd.oqteris.calc.model.Row;
import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporter for Office Open XML spreadsheets (.xlsx).
 */
public class XlsxExporter implements DocumentExporter<Workbook> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "XLSX",
            "Excel Workbook (*.xlsx)",
            DocumentType.CALC,
            List.of("xlsx"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new XlsxExporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canExport(File file) {
        if (file == null) return false;
        return file.getName().toLowerCase().endsWith(".xlsx");
    }

    @Override
    public void exportDocument(Workbook document, File file) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook poiWorkbook = new XSSFWorkbook()) {
            Map<CellStyle, org.apache.poi.xssf.usermodel.XSSFCellStyle> styleCache = new HashMap<>();

            for (Worksheet ws : document.getSheets()) {
                Sheet poiSheet = poiWorkbook.createSheet(ws.getName());

                // Export merged regions
                for (CellRangeAddress range : ws.getMergedRanges()) {
                    poiSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                            range.getFirstRow(), range.getLastRow(),
                            range.getFirstCol(), range.getLastCol()
                    ));
                }

                // Export rows & cells
                for (int r = 0; r <= ws.getMaxRow(); r++) {
                    Row row = ws.getRow(r);
                    if (row != null) {
                        org.apache.poi.ss.usermodel.Row poiRow = poiSheet.createRow(r);
                        poiRow.setHeightInPoints((float) (row.getHeight() / 1.33));

                        for (int c = 0; c <= ws.getMaxCol(); c++) {
                            Cell cell = row.getCell(c);
                            if (cell != null && cell.getType() != CellType.BLANK) {
                                org.apache.poi.ss.usermodel.Cell poiCell = poiRow.createCell(c);
                                writeCell(cell, poiCell);
                                applyStyle(cell, poiCell, poiWorkbook, styleCache);
                            }
                        }
                    }
                }
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                poiWorkbook.write(out);
            }
        }
    }

    private void applyStyle(Cell cell, org.apache.poi.ss.usermodel.Cell poiCell, org.apache.poi.xssf.usermodel.XSSFWorkbook poiWb, Map<CellStyle, org.apache.poi.xssf.usermodel.XSSFCellStyle> styleCache) {
        CellStyle style = cell.getStyle();
        if (style == null) return;

        org.apache.poi.xssf.usermodel.XSSFCellStyle poiStyle = styleCache.computeIfAbsent(style, s -> {
            org.apache.poi.xssf.usermodel.XSSFCellStyle ps = poiWb.createCellStyle();

            // Font
            org.apache.poi.xssf.usermodel.XSSFFont font = poiWb.createFont();
            font.setFontName(s.getFontFamily());
            font.setFontHeightInPoints((short) Math.round(s.getFontSize()));
            font.setBold(s.isBold());
            font.setItalic(s.isItalic());
            if (s.getTextColor() != null && !s.getTextColor().equals(javafx.scene.paint.Color.BLACK)) {
                byte[] rgb = toRgbBytes(s.getTextColor());
                font.setColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            }
            ps.setFont(font);

            // Fill color
            if (s.getFillColor() != null) {
                byte[] rgb = toRgbBytes(s.getFillColor());
                org.apache.poi.xssf.usermodel.XSSFColor fillCol = new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null);
                ps.setFillForegroundColor(fillCol);
                ps.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            }

            // Alignment
            switch (s.getAlignment()) {
                case CENTER -> ps.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                case RIGHT -> ps.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
                default -> ps.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
            }

            // Number format
            if (s.getNumberFormat() != null && !s.getNumberFormat().equalsIgnoreCase("general")) {
                org.apache.poi.ss.usermodel.DataFormat df = poiWb.createDataFormat();
                ps.setDataFormat(df.getFormat(s.getNumberFormat()));
            }

            return ps;
        });

        poiCell.setCellStyle(poiStyle);
    }

    private static byte[] toRgbBytes(javafx.scene.paint.Color color) {
        return new byte[]{
                (byte) Math.round(color.getRed() * 255),
                (byte) Math.round(color.getGreen() * 255),
                (byte) Math.round(color.getBlue() * 255)
        };
    }

    private void writeCell(Cell cell, org.apache.poi.ss.usermodel.Cell poiCell) {
        if (cell.getType() == CellType.FORMULA && cell.getFormula() != null) {
            try {
                poiCell.setCellFormula(cell.getFormula());
            } catch (Exception e) {
                poiCell.setCellValue(cell.getStringValue());
            }
        } else if (cell.getType() == CellType.NUMERIC) {
            poiCell.setCellValue(cell.getNumericValue());
        } else if (cell.getType() == CellType.BOOLEAN) {
            poiCell.setCellValue(cell.getBooleanValue());
        } else {
            poiCell.setCellValue(cell.getStringValue());
        }
    }
}
