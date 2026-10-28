package org.oqteris.calc.io;

import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.Workbook;
import org.oqteris.calc.model.Worksheet;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Importer for CSV / TSV text delimited files.
 */
public class CsvImporter implements DocumentImporter<Workbook> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "CSV",
            "Comma Delimited (*.csv, *.tsv)",
            DocumentType.CALC,
            List.of("csv", "tsv"),
            "text/csv",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new CsvImporter());
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
        return name.endsWith(".csv") || name.endsWith(".tsv");
    }

    @Override
    public Workbook importDocument(File file) throws IOException {
        Workbook workbook = new Workbook(file.getName());
        workbook.setFile(file);
        Worksheet ws = workbook.getActiveWorksheet();
        char delimiter = file.getName().toLowerCase().endsWith(".tsv") ? '\t' : ',';

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int rowIdx = 0;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsvLine(line, delimiter);
                for (int colIdx = 0; colIdx < values.size(); colIdx++) {
                    Cell cell = ws.getOrCreateCell(rowIdx, colIdx);
                    cell.setValue(values.get(colIdx));
                }
                rowIdx++;
            }
        }

        workbook.recalculate();
        workbook.setModified(false);
        return workbook;
    }

    public static List<String> parseCsvLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }
}
