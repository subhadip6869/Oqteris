package org.oqteris.calc.io;

import org.oqteris.calc.model.*;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Importer for OpenDocument Spreadsheet (.ods) files.
 */
public class OdsImporter implements DocumentImporter<Workbook> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "ODS",
            "OpenDocument Spreadsheet (*.ods)",
            DocumentType.CALC,
            List.of("ods"),
            "application/vnd.oasis.opendocument.spreadsheet",
            true,
            false
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new OdsImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".ods");
    }

    @Override
    public Workbook importDocument(File file) throws IOException {
        Workbook workbook = new Workbook(file.getName());
        workbook.setFile(file);
        workbook.clearSheets();

        try (FileInputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("content.xml".equalsIgnoreCase(entry.getName())) {
                    parseContentXml(zis, workbook);
                    break;
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse ODS spreadsheet: " + e.getMessage(), e);
        }

        if (workbook.getSheetCount() == 0) {
            workbook.createSheet("Sheet1");
        }

        workbook.recalculate();
        workbook.setModified(false);
        return workbook;
    }

    private void parseContentXml(InputStream is, Workbook workbook) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(is);

        NodeList tableNodes = dom.getElementsByTagNameNS("*", "table");
        for (int t = 0; t < tableNodes.getLength(); t++) {
            Element tableEl = (Element) tableNodes.item(t);
            String sheetName = tableEl.getAttributeNS("*", "name");
            if (sheetName == null || sheetName.isBlank()) {
                sheetName = "Sheet" + (t + 1);
            }
            Worksheet ws = workbook.createSheet(sheetName);

            NodeList rowNodes = tableEl.getElementsByTagNameNS("*", "table-row");
            int rowIndex = 0;

            for (int r = 0; r < rowNodes.getLength(); r++) {
                Element rowEl = (Element) rowNodes.item(r);
                int repeatRows = parseRepeatCount(rowEl);
                for (int repR = 0; repR < repeatRows; repR++) {
                    NodeList cellNodes = rowEl.getElementsByTagNameNS("*", "table-cell");
                    int colIndex = 0;

                    for (int c = 0; c < cellNodes.getLength(); c++) {
                        Element cellEl = (Element) cellNodes.item(c);
                        int repeatCols = parseRepeatCount(cellEl);

                        String formula = cellEl.getAttributeNS("*", "formula");
                        String valType = cellEl.getAttributeNS("*", "value-type");
                        String rawVal = cellEl.getAttributeNS("*", "value");

                        // Extract text representation from child text:p
                        NodeList pNodes = cellEl.getElementsByTagNameNS("*", "p");
                        String textVal = pNodes.getLength() > 0 ? pNodes.item(0).getTextContent() : "";

                        for (int repC = 0; repC < repeatCols; repC++) {
                            if ((rawVal != null && !rawVal.isBlank()) || !textVal.isBlank() || (formula != null && !formula.isBlank())) {
                                Row row = ws.getOrCreateRow(rowIndex);
                                Cell cell = row.getOrCreateCell(colIndex);

                                if (formula != null && !formula.isBlank()) {
                                    String cleanFormula = formula;
                                    if (cleanFormula.startsWith("of:=")) cleanFormula = cleanFormula.substring(4);
                                    else if (cleanFormula.startsWith("=")) cleanFormula = cleanFormula.substring(1);
                                    cell.setFormula(cleanFormula);
                                }

                                if ("float".equals(valType) || "currency".equals(valType) || "percentage".equals(valType)) {
                                    try {
                                        cell.setValue(Double.parseDouble(rawVal));
                                    } catch (Exception ex) {
                                        cell.setValue(textVal);
                                    }
                                } else if ("boolean".equals(valType)) {
                                    cell.setValue(Boolean.parseBoolean(cellEl.getAttributeNS("*", "boolean-value")));
                                } else {
                                    cell.setValue(!textVal.isEmpty() ? textVal : rawVal);
                                }
                            }
                            colIndex++;
                        }
                    }
                    rowIndex++;
                    if (rowIndex > 1000) break; // Reasonable sanity cap on empty repeating rows
                }
                if (rowIndex > 1000) break;
            }
        }
    }

    private int parseRepeatCount(Element el) {
        String rep = el.getAttributeNS("*", "number-rows-repeated");
        if (rep == null || rep.isBlank()) {
            rep = el.getAttributeNS("*", "number-columns-repeated");
        }
        if (rep != null && !rep.isBlank()) {
            try {
                int count = Integer.parseInt(rep);
                return Math.min(count, 50); // Cap repeated empty cells
            } catch (Exception ignored) {
            }
        }
        return 1;
    }
}
