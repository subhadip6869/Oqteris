package org.oqteris.calc.formula;

import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.CellAddress;
import org.oqteris.calc.model.Worksheet;

import java.util.List;

/**
 * Context provided during formula expression evaluation.
 */
public interface EvaluationContext {
    Worksheet getActiveSheet();

    Worksheet getSheet(String name);

    Cell getCell(CellAddress address);

    List<Cell> getRangeCells(String sheetName, int r1, int r2, int c1, int c2);
}
