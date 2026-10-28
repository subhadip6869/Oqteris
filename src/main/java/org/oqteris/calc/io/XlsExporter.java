package org.oqteris.calc.io;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.oqteris.calc.model.*;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporter for legacy Microsoft Excel 97-2003 binary workbooks (.xls).
 */
public class XlsExporter implements DocumentExporter<Workbook> {
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
        DocumentRegistry.getInstance().registerExporter(new XlsExporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canExport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".xls");
    }

    @Override
    public void exportDocument(Workbook workbook, File targetFile) throws IOException {
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        try (org.apache.poi.ss.usermodel.Workbook poiWorkbook = new HSSFWorkbook();
             FileOutputStream out = new FileOutputStream(targetFile)) {

            Map<String, org.apache.poi.ss.usermodel.CellStyle> styleCache = new HashMap<>();

            for (Worksheet ws : workbook.getSheets()) {
                Sheet poiSheet = poiWorkbook.createSheet(ws.getName());

                // Column widths
                for (int c = 0; c < 50; c++) {
                    double w = ws.getColumnWidth(c);
                    poiSheet.setColumnWidth(c, (int) Math.round(w * 36.5));
                }

                // Merged ranges
                for (CellRangeAddress range : ws.getMergedRanges()) {
                    poiSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                            range.getFirstRow(), range.getLastRow(),
                            range.getFirstCol(), range.getLastCol()
                    ));
                }

                // Rows & cells
                for (int r = 0; r <= ws.getMaxRow(); r++) {
                    Row row = ws.getRow(r);
                    if (row != null) {
                        org.apache.poi.ss.usermodel.Row poiRow = poiSheet.createRow(r);
                        poiRow.setHeightInPoints((float) (row.getHeight() / 1.33));

                        for (int c = 0; c <= ws.getMaxCol(); c++) {
                            Cell cell = row.getCell(c);
                            if (cell != null && cell.getType() != CellType.BLANK) {
                                org.apache.poi.ss.usermodel.Cell poiCell = poiRow.createCell(c);
                                writeCell(poiWorkbook, poiCell, cell, styleCache);
                            }
                        }
                    }
                }
            }

            poiWorkbook.write(out);
        }
    }

    private void writeCell(org.apache.poi.ss.usermodel.Workbook poiWorkbook, org.apache.poi.ss.usermodel.Cell poiCell,
                           Cell cell, Map<String, org.apache.poi.ss.usermodel.CellStyle> styleCache) {
        if (cell.getFormula() != null && !cell.getFormula().isBlank()) {
            try {
                poiCell.setCellFormula(cell.getFormula());
            } catch (Exception ignored) {
                if (cell.getRawValue() != null) {
                    poiCell.setCellValue(cell.getStringValue());
                }
            }
        } else if (cell.getRawValue() instanceof Number n) {
            poiCell.setCellValue(n.doubleValue());
        } else if (cell.getRawValue() instanceof Boolean b) {
            poiCell.setCellValue(b);
        } else if (cell.getRawValue() != null) {
            poiCell.setCellValue(cell.getStringValue());
        }

        // Apply style
        CellStyle s = cell.getStyle();
        if (s != null) {
            String key = s.getFontFamily() + "_" + s.getFontSize() + "_" + s.isBold() + "_" + s.isItalic() + "_" + s.getAlignment();
            org.apache.poi.ss.usermodel.CellStyle poiStyle = styleCache.computeIfAbsent(key, k -> {
                org.apache.poi.ss.usermodel.CellStyle cs = poiWorkbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font font = poiWorkbook.createFont();
                font.setFontName(s.getFontFamily());
                font.setFontHeightInPoints((short) Math.round(s.getFontSize()));
                font.setBold(s.isBold());
                font.setItalic(s.isItalic());
                cs.setFont(font);

                if (s.getAlignment() == CellStyle.Alignment.CENTER) {
                    cs.setAlignment(HorizontalAlignment.CENTER);
                } else if (s.getAlignment() == CellStyle.Alignment.RIGHT) {
                    cs.setAlignment(HorizontalAlignment.RIGHT);
                } else {
                    cs.setAlignment(HorizontalAlignment.LEFT);
                }
                return cs;
            });

            poiCell.setCellStyle(poiStyle);
        }
    }
}
