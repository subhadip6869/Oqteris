package org.oqteris.calc;

import org.junit.jupiter.api.Test;
import org.oqteris.calc.format.CellNumberFormatter;
import org.oqteris.calc.io.CsvExporter;
import org.oqteris.calc.io.CsvImporter;
import org.oqteris.calc.model.*;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class SpreadsheetModelTest {

    @Test
    public void testNumberFormatting() {
        assertEquals("1234.56", CellNumberFormatter.format(1234.56, "General"));
        assertEquals("42", CellNumberFormatter.format(42.0, "General"));
        assertEquals("$1,234.56", CellNumberFormatter.format(1234.56, "$#,##0.00"));
        assertEquals("75.50%", CellNumberFormatter.format(0.755, "0.00%"));
    }

    @Test
    public void testMergedCells() {
        Worksheet ws = new Worksheet("Sheet1");
        CellRangeAddress range = new CellRangeAddress(1, 3, 2, 5); // B2:E4
        ws.addMergedRange(range);

        assertEquals(range, ws.getMergedRangeAt(2, 3));
        assertTrue(ws.isMergedMaster(1, 2));
        assertTrue(ws.isMergedSlave(2, 3));
        assertFalse(ws.isMergedSlave(1, 2));
        assertNull(ws.getMergedRangeAt(0, 0));
    }

    @Test
    public void testRangeSorting() {
        Worksheet ws = new Worksheet("SortTest");
        ws.getOrCreateCell(0, 0).setValue("Banana");
        ws.getOrCreateCell(0, 1).setValue(30.0);

        ws.getOrCreateCell(1, 0).setValue("Apple");
        ws.getOrCreateCell(1, 1).setValue(50.0);

        ws.getOrCreateCell(2, 0).setValue("Cherry");
        ws.getOrCreateCell(2, 1).setValue(20.0);

        // Sort ascending by column 0 (names)
        CellRangeAddress range = new CellRangeAddress(0, 2, 0, 1);
        ws.sortRange(range, 0, true);

        assertEquals("Apple", ws.getCell(0, 0).getStringValue());
        assertEquals(50.0, ws.getCell(0, 1).getNumericValue());

        assertEquals("Banana", ws.getCell(1, 0).getStringValue());
        assertEquals("Cherry", ws.getCell(2, 0).getStringValue());

        // Sort ascending by column 1 (numbers)
        ws.sortRange(range, 1, true);
        assertEquals(20.0, ws.getCell(0, 1).getNumericValue());
        assertEquals("Cherry", ws.getCell(0, 0).getStringValue());

        assertEquals(30.0, ws.getCell(1, 1).getNumericValue());
        assertEquals(50.0, ws.getCell(2, 1).getNumericValue());
    }

    @Test
    public void testCsvRoundTrip() throws IOException {
        Workbook wb = new Workbook("CsvRoundTrip");
        Worksheet ws = wb.getActiveWorksheet();
        ws.getOrCreateCell(0, 0).setValue("ID");
        ws.getOrCreateCell(0, 1).setValue("Name");
        ws.getOrCreateCell(0, 2).setValue("Salary");

        ws.getOrCreateCell(1, 0).setValue(101.0);
        ws.getOrCreateCell(1, 1).setValue("Alice Smith");
        ws.getOrCreateCell(1, 2).setValue(75000.0);

        File tempCsv = File.createTempFile("oqteris_test_", ".csv");
        tempCsv.deleteOnExit();

        CsvExporter exporter = new CsvExporter();
        exporter.exportDocument(wb, tempCsv);

        CsvImporter importer = new CsvImporter();
        Workbook importedWb = importer.importDocument(tempCsv);
        Worksheet importedWs = importedWb.getActiveWorksheet();

        assertEquals("ID", importedWs.getCell(0, 0).getStringValue());
        assertEquals("Alice Smith", importedWs.getCell(1, 1).getStringValue());
        assertEquals(75000.0, importedWs.getCell(1, 2).getNumericValue());
    }
}
