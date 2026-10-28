package org.originsd.oqteris.calc.formula;

import org.originsd.oqteris.calc.model.*;

import java.util.*;

/**
 * High-performance spreadsheet recalculation engine with dependency tracking and cycle detection.
 */
public class CalculationEngine {
    private final DependencyGraph dependencyGraph = new DependencyGraph();

    public CalculationEngine() {
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    /**
     * Evaluates a single cell formula within the workbook context.
     */
    public Object evaluateCell(Workbook workbook, Worksheet sheet, Cell cell) {
        if (cell == null || cell.getType() != CellType.FORMULA || cell.getFormula() == null) {
            return cell != null ? cell.getRawValue() : null;
        }

        CellAddress cellAddr = new CellAddress(sheet.getName(), cell.getRow(), cell.getCol());

        try {
            ExpressionNode ast = FormulaParser.parse(cell.getFormula());

            // Track dependencies
            Set<CellAddress> refs = new HashSet<>();
            ast.collectReferences(sheet.getName(), refs);
            dependencyGraph.setDependencies(cellAddr, refs);

            // Check circular dependency
            if (dependencyGraph.hasCycle(cellAddr)) {
                cell.setCachedEvaluatedValue("#CYCLE!");
                return "#CYCLE!";
            }

            EvaluationContext ctx = createEvaluationContext(workbook, sheet);
            Object result = ast.evaluate(ctx);
            cell.setCachedEvaluatedValue(result);
            return result;
        } catch (Exception e) {
            cell.setCachedEvaluatedValue("#VALUE!");
            return "#VALUE!";
        }
    }

    /**
     * Recalculates all dependent cells after a change to a cell.
     */
    public void notifyCellChanged(Workbook workbook, Worksheet sheet, CellAddress changedCell) {
        List<CellAddress> evalOrder = dependencyGraph.getEvaluationOrder(changedCell);
        for (CellAddress addr : evalOrder) {
            Worksheet s = workbook.getSheet(addr.getSheetName() != null ? addr.getSheetName() : sheet.getName());
            if (s != null) {
                Cell c = s.getCell(addr);
                if (c != null && c.getType() == CellType.FORMULA) {
                    evaluateCell(workbook, s, c);
                }
            }
        }
    }

    /**
     * Recalculates all formulas across the entire workbook.
     */
    public void recalculateWorkbook(Workbook workbook) {
        if (workbook == null) return;
        for (Worksheet sheet : workbook.getSheets()) {
            for (int r = 0; r <= sheet.getMaxRow(); r++) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    for (Cell cell : row.getCells().values()) {
                        if (cell.getType() == CellType.FORMULA) {
                            evaluateCell(workbook, sheet, cell);
                        }
                    }
                }
            }
        }
    }

    public static EvaluationContext createEvaluationContext(Workbook workbook, Worksheet activeSheet) {
        return new EvaluationContext() {
            @Override
            public Worksheet getActiveSheet() {
                return activeSheet;
            }

            @Override
            public Worksheet getSheet(String name) {
                return workbook != null ? workbook.getSheet(name) : activeSheet;
            }

            @Override
            public Cell getCell(CellAddress address) {
                if (address == null) return null;
                Worksheet s = (address.getSheetName() != null && workbook != null) ?
                        workbook.getSheet(address.getSheetName()) : activeSheet;
                return s != null ? s.getCell(address.getRow(), address.getCol()) : null;
            }

            @Override
            public List<Cell> getRangeCells(String sheetName, int r1, int r2, int c1, int c2) {
                List<Cell> cells = new ArrayList<>();
                Worksheet s = (sheetName != null && workbook != null) ?
                        workbook.getSheet(sheetName) : activeSheet;
                if (s != null) {
                    for (int r = r1; r <= r2; r++) {
                        for (int c = c1; c <= c2; c++) {
                            cells.add(s.getCell(r, c));
                        }
                    }
                }
                return cells;
            }
        };
    }
}
