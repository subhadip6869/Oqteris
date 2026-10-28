package org.oqteris.calc.io;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.oqteris.calc.model.*;
import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.Row;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
        try (org.apache.poi.ss.usermodel.Workbook poiWorkbook = new XSSFWorkbook()) {

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
