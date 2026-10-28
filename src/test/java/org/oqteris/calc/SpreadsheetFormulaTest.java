package org.oqteris.calc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oqteris.calc.formula.CalculationEngine;
import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.CellAddress;
import org.oqteris.calc.model.Workbook;
import org.oqteris.calc.model.Worksheet;

import static org.junit.jupiter.api.Assertions.*;

public class SpreadsheetFormulaTest {
    private Workbook workbook;
    private Worksheet sheet;
    private CalculationEngine engine;

    @BeforeEach
    public void setUp() {
        workbook = new Workbook("TestBook");
        sheet = workbook.getActiveWorksheet();
        engine = workbook.getCalculationEngine();
    }

    @Test
    public void testBasicArithmetic() {
        Cell c1 = sheet.getOrCreateCell(0, 0); // A1
        c1.setFormula("=10 + 20 * 3");
        assertEquals(70.0, engine.evaluateCell(workbook, sheet, c1));

        Cell c2 = sheet.getOrCreateCell(0, 1); // B1
        c2.setFormula("=(10 + 20) * 3");
        assertEquals(90.0, engine.evaluateCell(workbook, sheet, c2));

        Cell c3 = sheet.getOrCreateCell(0, 2); // C1
        c3.setFormula("=2 ^ 3");
        assertEquals(8.0, engine.evaluateCell(workbook, sheet, c3));

        Cell c4 = sheet.getOrCreateCell(0, 3); // D1
        c4.setFormula("=10 / 0");
        assertEquals("#DIV/0!", engine.evaluateCell(workbook, sheet, c4));
    }

    @Test
    public void testCellReferences() {
        sheet.getOrCreateCell(0, 0).setValue(15.0); // A1 = 15
        sheet.getOrCreateCell(1, 0).setValue(25.0); // A2 = 25

        Cell a3 = sheet.getOrCreateCell(2, 0); // A3
        a3.setFormula("=A1 + A2");
        assertEquals(40.0, engine.evaluateCell(workbook, sheet, a3));

        // Update A1 and verify dependency notification
        sheet.getOrCreateCell(0, 0).setValue(20.0);
        engine.notifyCellChanged(workbook, sheet, new CellAddress(sheet.getName(), 0, 0));
        assertEquals(45.0, a3.getEvaluatedValue());
    }

    @Test
    public void testMathFunctions() {
        sheet.getOrCreateCell(0, 0).setValue(10.0);
        sheet.getOrCreateCell(1, 0).setValue(20.0);
        sheet.getOrCreateCell(2, 0).setValue(30.0);
        sheet.getOrCreateCell(3, 0).setValue(40.0);

        Cell sumCell = sheet.getOrCreateCell(4, 0);
        sumCell.setFormula("=SUM(A1:A4)");
        assertEquals(100.0, engine.evaluateCell(workbook, sheet, sumCell));

        Cell avgCell = sheet.getOrCreateCell(5, 0);
        avgCell.setFormula("=AVERAGE(A1:A4)");
        assertEquals(25.0, engine.evaluateCell(workbook, sheet, avgCell));

        Cell minCell = sheet.getOrCreateCell(6, 0);
        minCell.setFormula("=MIN(A1:A4)");
        assertEquals(10.0, engine.evaluateCell(workbook, sheet, minCell));

        Cell maxCell = sheet.getOrCreateCell(7, 0);
        maxCell.setFormula("=MAX(A1:A4)");
        assertEquals(40.0, engine.evaluateCell(workbook, sheet, maxCell));

        Cell countCell = sheet.getOrCreateCell(8, 0);
        countCell.setFormula("=COUNT(A1:A4)");
        assertEquals(4.0, engine.evaluateCell(workbook, sheet, countCell));
    }

    @Test
    public void testLogicFunctions() {
        Cell ifCell = sheet.getOrCreateCell(0, 0);
        ifCell.setFormula("=IF(10 > 5, \"Pass\", \"Fail\")");
        assertEquals("Pass", engine.evaluateCell(workbook, sheet, ifCell));

        Cell ifCell2 = sheet.getOrCreateCell(0, 1);
        ifCell2.setFormula("=IF(2 + 2 = 5, \"Wrong\", \"Right\")");
        assertEquals("Right", engine.evaluateCell(workbook, sheet, ifCell2));

        Cell andCell = sheet.getOrCreateCell(0, 2);
        andCell.setFormula("=AND(5 > 2, 10 > 5, 1 > 0)");
        assertEquals(true, engine.evaluateCell(workbook, sheet, andCell));

        Cell orCell = sheet.getOrCreateCell(0, 3);
        orCell.setFormula("=OR(5 < 2, 10 < 5, 1 > 0)");
        assertEquals(true, engine.evaluateCell(workbook, sheet, orCell));
    }

    @Test
    public void testTextFunctions() {
        Cell c1 = sheet.getOrCreateCell(0, 0);
        c1.setFormula("=CONCAT(\"Hello\", \" \", \"World\")");
        assertEquals("Hello World", engine.evaluateCell(workbook, sheet, c1));

        Cell c2 = sheet.getOrCreateCell(0, 1);
        c2.setFormula("=UPPER(\"oqteris\")");
        assertEquals("OQTERIS", engine.evaluateCell(workbook, sheet, c2));

        Cell c3 = sheet.getOrCreateCell(0, 2);
        c3.setFormula("=LEFT(\"Desktop Office\", 7)");
        assertEquals("Desktop", engine.evaluateCell(workbook, sheet, c3));

        Cell c4 = sheet.getOrCreateCell(0, 3);
        c4.setFormula("=LEN(\"Oqteris\")");
        assertEquals(7.0, engine.evaluateCell(workbook, sheet, c4));
    }

    @Test
    public void testCircularDependencyDetection() {
        Cell a1 = sheet.getOrCreateCell(0, 0);
        Cell b1 = sheet.getOrCreateCell(0, 1);

        a1.setFormula("=B1 + 5");
        b1.setFormula("=A1 + 10");

        engine.evaluateCell(workbook, sheet, a1);
        Object res = engine.evaluateCell(workbook, sheet, b1);
        assertEquals("#CYCLE!", res);
    }
}
