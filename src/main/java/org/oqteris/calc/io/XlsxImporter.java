package org.oqteris.calc.io;

import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.CellRangeAddress;
import org.oqteris.calc.model.CellStyle;
import org.oqteris.calc.model.Row;
import org.oqteris.calc.model.Workbook;
import org.oqteris.calc.model.Worksheet;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for Office Open XML spreadsheets (.xlsx).
 */
public class XlsxImporter implements DocumentImporter<Workbook> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "XLSX",
            "Excel Workbook (*.xlsx)",
            DocumentType.CALC,
            List.of("xlsx", "xlsm"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new XlsxImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".xlsx") || name.endsWith(".xlsm");
    }

    @Override
    public Workbook importDocument(File file) throws IOException {
        Workbook workbook = new Workbook(file.getName());
        workbook.setFile(file);

        try (FileInputStream in = new FileInputStream(file);
             org.apache.poi.ss.usermodel.Workbook poiWorkbook = new XSSFWorkbook(in)) {

            // Clear default sheet
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
                        row.setHeight(poiRow.getHeightInPoints() * 1.33); // Convert pt to px
                    }

                    for (org.apache.poi.ss.usermodel.Cell poiCell : poiRow) {
                        int cIdx = poiCell.getColumnIndex();
                        Cell cell = row.getOrCreateCell(cIdx);
                        readCell(poiCell, cell);
                    }
                }
            }

            // Recalculate imported formulas
            workbook.recalculate();
            workbook.setModified(false);
        }

        return workbook;
    }

    private void readCell(org.apache.poi.ss.usermodel.Cell poiCell, Cell cell) {
        org.apache.poi.ss.usermodel.CellType poiType = poiCell.getCellType();
        if (poiType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            try {
                cell.setFormula(poiCell.getCellFormula());
            } catch (Exception ignored) {
            }
        } else if (poiType == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(poiCell)) {
                cell.setValue(poiCell.getDateCellValue());
            } else {
                cell.setValue(poiCell.getNumericCellValue());
            }
        } else if (poiType == org.apache.poi.ss.usermodel.CellType.BOOLEAN) {
            cell.setValue(poiCell.getBooleanCellValue());
        } else if (poiType == org.apache.poi.ss.usermodel.CellType.STRING) {
            cell.setValue(poiCell.getStringCellValue());
        }

        // Style
        org.apache.poi.ss.usermodel.CellStyle poiStyle = poiCell.getCellStyle();
        if (poiStyle != null) {
            CellStyle style = cell.getStyle();
            String formatStr = poiStyle.getDataFormatString();
            if (formatStr != null && !formatStr.isBlank()) {
                style.setNumberFormat(formatStr);
            }
        }
    }
}
