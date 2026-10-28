package org.oqteris.calc.io;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.oqteris.calc.model.*;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for legacy Microsoft Excel 97-2003 binary workbooks (.xls).
 */
public class XlsImporter implements DocumentImporter<Workbook> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "XLS",
            "Excel 97-2003 Workbook (*.xls)",
            DocumentType.CALC,
            List.of("xls"),
            "application/vnd.ms-excel",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new XlsImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".xls");
    }

    @Override
    public Workbook importDocument(File file) throws IOException {
        Workbook workbook = new Workbook(file.getName());
        workbook.setFile(file);

        try (FileInputStream in = new FileInputStream(file);
             org.apache.poi.ss.usermodel.Workbook poiWorkbook = new HSSFWorkbook(in)) {

            workbook.clearSheets();

            for (int s = 0; s < poiWorkbook.getNumberOfSheets(); s++) {
                Sheet poiSheet = poiWorkbook.getSheetAt(s);
                Worksheet ws = workbook.createSheet(poiSheet.getSheetName());

                // Read merged regions
                for (int m = 0; m < poiSheet.getNumMergedRegions(); m++) {
                    org.apache.poi.ss.util.CellRangeAddress poiRange = poiSheet.getMergedRegion(m);
                    ws.addMergedRange(new CellRangeAddress(
                            poiRange.getFirstRow(), poiRange.getLastRow(),
                            poiRange.getFirstColumn(), poiRange.getLastColumn()
                    ));
                }

                // Read rows & cells
                for (org.apache.poi.ss.usermodel.Row poiRow : poiSheet) {
                    int rIdx = poiRow.getRowNum();
                    Row row = ws.getOrCreateRow(rIdx);
                    if (poiRow.getHeightInPoints() > 0) {
                        row.setHeight(poiRow.getHeightInPoints() * 1.33);
                    }

                    for (org.apache.poi.ss.usermodel.Cell poiCell : poiRow) {
                        int cIdx = poiCell.getColumnIndex();
                        Cell cell = row.getOrCreateCell(cIdx);
                        readCell(poiCell, cell);
                    }
                }
            }

            workbook.recalculate();
            workbook.setModified(false);
        }

        return workbook;
    }

    private void readCell(org.apache.poi.ss.usermodel.Cell poiCell, Cell cell) {
        switch (poiCell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(poiCell)) {
                    cell.setValue(poiCell.getDateCellValue().toString());
                } else {
                    cell.setValue(poiCell.getNumericCellValue());
                }
            }
            case STRING -> cell.setValue(poiCell.getStringCellValue());
            case BOOLEAN -> cell.setValue(poiCell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    cell.setFormula(poiCell.getCellFormula());
                } catch (Exception ignored) {}
                try {
                    switch (poiCell.getCachedFormulaResultType()) {
                        case NUMERIC -> cell.setCachedEvaluatedValue(poiCell.getNumericCellValue());
                        case STRING -> cell.setCachedEvaluatedValue(poiCell.getStringCellValue());
                        case BOOLEAN -> cell.setCachedEvaluatedValue(poiCell.getBooleanCellValue());
                        default -> {}
                    }
                } catch (Exception ignored) {}
            }
            case ERROR -> cell.setValue("#ERR:" + poiCell.getErrorCellValue());
            case BLANK -> cell.setValue(null);
        }

        // Styles
        org.apache.poi.ss.usermodel.CellStyle poiStyle = poiCell.getCellStyle();
        if (poiStyle != null) {
            CellStyle style = cell.getStyle();
            org.apache.poi.ss.usermodel.Workbook poiWb = poiCell.getSheet().getWorkbook();
            org.apache.poi.ss.usermodel.Font poiFont = poiWb.getFontAt(poiStyle.getFontIndex());
            if (poiFont != null) {
                style.setFontFamily(poiFont.getFontName());
                style.setFontSize((double) poiFont.getFontHeightInPoints());
                style.setBold(poiFont.getBold());
                style.setItalic(poiFont.getItalic());
            }

            // HSSF palette for colors
            if (poiWb instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook hssfWb) {
                org.apache.poi.hssf.usermodel.HSSFPalette palette = hssfWb.getCustomPalette();
                if (palette != null) {
                    // Fill color
                    if (poiStyle.getFillPattern() != org.apache.poi.ss.usermodel.FillPatternType.NO_FILL) {
                        short fillIdx = poiStyle.getFillForegroundColor();
                        org.apache.poi.hssf.util.HSSFColor hssfFill = palette.getColor(fillIdx);
                        if (hssfFill != null) {
                            short[] triplet = hssfFill.getTriplet();
                            style.setFillColor(javafx.scene.paint.Color.rgb(triplet[0], triplet[1], triplet[2]));
                        }
                    }
                    // Font color
                    if (poiFont != null) {
                        short fontColorIdx = poiFont.getColor();
                        org.apache.poi.hssf.util.HSSFColor hssfText = palette.getColor(fontColorIdx);
                        if (hssfText != null) {
                            short[] triplet = hssfText.getTriplet();
                            style.setTextColor(javafx.scene.paint.Color.rgb(triplet[0], triplet[1], triplet[2]));
                        }
                    }
                }
            }

            switch (poiStyle.getAlignment()) {
                case CENTER -> style.setAlignment(CellStyle.Alignment.CENTER);
                case RIGHT -> style.setAlignment(CellStyle.Alignment.RIGHT);
                default -> style.setAlignment(CellStyle.Alignment.LEFT);
            }

            // Borders
            if (poiStyle.getBorderTop() != org.apache.poi.ss.usermodel.BorderStyle.NONE) {
                style.setBorderTop(CellStyle.BorderType.THIN);
            }
            if (poiStyle.getBorderBottom() != org.apache.poi.ss.usermodel.BorderStyle.NONE) {
                style.setBorderBottom(CellStyle.BorderType.THIN);
            }
            if (poiStyle.getBorderLeft() != org.apache.poi.ss.usermodel.BorderStyle.NONE) {
                style.setBorderLeft(CellStyle.BorderType.THIN);
            }
            if (poiStyle.getBorderRight() != org.apache.poi.ss.usermodel.BorderStyle.NONE) {
                style.setBorderRight(CellStyle.BorderType.THIN);
            }

            String formatStr = poiStyle.getDataFormatString();
            if (formatStr != null && !formatStr.equalsIgnoreCase("general")) {
                style.setNumberFormat(formatStr);
            }
        }
    }
}
