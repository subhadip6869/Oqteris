package org.oqteris.calc.model;

import org.oqteris.core.model.AbstractDocumentModel;
import org.oqteris.core.model.DocumentType;
import org.oqteris.calc.formula.CalculationEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-level spreadsheet document model holding worksheets and calculation engine.
 */
public class Workbook extends AbstractDocumentModel {
    private final List<Worksheet> sheets = new ArrayList<>();
    private int activeSheetIndex = 0;
    private final CalculationEngine calculationEngine = new CalculationEngine();

    public Workbook() {
        this("Workbook 1");
    }

    public Workbook(String defaultTitle) {
        super(DocumentType.CALC, defaultTitle);
        Worksheet defaultSheet = new Worksheet("Sheet1");
        sheets.add(defaultSheet);
    }

    public List<Worksheet> getSheets() {
        return Collections.unmodifiableList(sheets);
    }

    public int getSheetCount() {
        return sheets.size();
    }

    public Worksheet getSheet(int index) {
        if (index >= 0 && index < sheets.size()) {
            return sheets.get(index);
        }
        return null;
    }

    public Worksheet getSheet(String name) {
        if (name == null) return null;
        for (Worksheet s : sheets) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public Worksheet getActiveWorksheet() {
        if (activeSheetIndex >= 0 && activeSheetIndex < sheets.size()) {
            return sheets.get(activeSheetIndex);
        }
        return sheets.isEmpty() ? null : sheets.get(0);
    }

    public int getActiveSheetIndex() {
        return activeSheetIndex;
    }

    public void setActiveSheetIndex(int activeSheetIndex) {
        if (activeSheetIndex >= 0 && activeSheetIndex < sheets.size()) {
            this.activeSheetIndex = activeSheetIndex;
            notifyChanged();
        }
    }

    public Worksheet createSheet(String name) {
        String sheetName = name != null ? name : "Sheet" + (sheets.size() + 1);
        Worksheet s = new Worksheet(sheetName);
        sheets.add(s);
        setModified(true);
        notifyChanged();
        return s;
    }

    public void clearSheets() {
        sheets.clear();
        activeSheetIndex = 0;
        setModified(true);
        notifyChanged();
    }

    public void removeSheet(int index) {
        if (sheets.size() > 1 && index >= 0 && index < sheets.size()) {
            sheets.remove(index);
            if (activeSheetIndex >= sheets.size()) {
                activeSheetIndex = sheets.size() - 1;
            }
            setModified(true);
            notifyChanged();
        }
    }

    public CalculationEngine getCalculationEngine() {
        return calculationEngine;
    }

    public void recalculate() {
        calculationEngine.recalculateWorkbook(this);
        notifyChanged();
    }
}
