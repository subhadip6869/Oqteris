package org.oqteris.calc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oqteris.calc.io.XlsExporter;
import org.oqteris.calc.io.XlsImporter;
import org.oqteris.calc.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class XlsRoundTripTest {

    @Test
    public void testXlsExportAndImport(@TempDir Path tempDir) throws IOException {
        Workbook workbook = new Workbook("Financials.xls");
        Worksheet sheet = workbook.getActiveWorksheet();
        sheet.setName("Q1 Budget");

        // Headers
        sheet.getOrCreateCell(0, 0).setValue("Category");
        sheet.getOrCreateCell(0, 1).setValue("Amount");

        // Data
        sheet.getOrCreateCell(1, 0).setValue("Rent");
        sheet.getOrCreateCell(1, 1).setValue(1500.0);

        sheet.getOrCreateCell(2, 0).setValue("Utilities");
        sheet.getOrCreateCell(2, 1).setValue(350.0);

        sheet.getOrCreateCell(3, 0).setValue("Salaries");
        sheet.getOrCreateCell(3, 1).setValue(4200.0);

        // Formula
        Cell totalCell = sheet.getOrCreateCell(4, 1);
        totalCell.setFormula("SUM(B2:B4)");

        workbook.recalculate();
        assertEquals(6050.0, totalCell.getNumericValue(), 0.001);

        // Add merged range
        sheet.addMergedRange(new CellRangeAddress(5, 5, 0, 1));

        File file = tempDir.resolve("budget.xls").toFile();

        XlsExporter exporter = new XlsExporter();
        assertTrue(exporter.canExport(file));
        exporter.exportDocument(workbook, file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        XlsImporter importer = new XlsImporter();
        assertTrue(importer.canImport(file));
        Workbook loaded = importer.importDocument(file);

        assertNotNull(loaded);
        assertEquals(1, loaded.getSheetCount());
        Worksheet loadedSheet = loaded.getSheet(0);
        assertEquals("Q1 Budget", loadedSheet.getName());

        assertEquals("Category", loadedSheet.getCell(0, 0).getStringValue());
        assertEquals("Rent", loadedSheet.getCell(1, 0).getStringValue());
        assertEquals(1500.0, loadedSheet.getCell(1, 1).getNumericValue(), 0.001);

        Cell loadedTotal = loadedSheet.getCell(4, 1);
        assertEquals("SUM(B2:B4)", loadedTotal.getFormula());
        assertEquals(6050.0, loadedTotal.getNumericValue(), 0.001);

        assertEquals(1, loadedSheet.getMergedRanges().size());
    }
}
