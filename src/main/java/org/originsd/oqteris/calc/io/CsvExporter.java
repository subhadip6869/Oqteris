package org.originsd.oqteris.calc.io;

import org.originsd.oqteris.calc.model.Cell;
import org.originsd.oqteris.calc.model.Workbook;
import org.originsd.oqteris.calc.model.Worksheet;
import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Exporter for CSV / TSV text delimited files.
 */
public class CsvExporter implements DocumentExporter<Workbook> {
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
        DocumentRegistry.getInstance().registerExporter(new CsvExporter());
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
        String name = file.getName().toLowerCase();
        return name.endsWith(".csv") || name.endsWith(".tsv");
    }

    @Override
    public void exportDocument(Workbook document, File file) throws IOException {
        Worksheet ws = document.getActiveWorksheet();
        char delimiter = file.getName().toLowerCase().endsWith(".tsv") ? '\t' : ',';

        try (FileWriter writer = new FileWriter(file)) {
            int maxR = ws.getMaxRow();
            int maxC = ws.getMaxCol();

            for (int r = 0; r <= maxR; r++) {
                for (int c = 0; c <= maxC; c++) {
                    Cell cell = ws.getCell(r, c);
                    String val = cell != null ? cell.getDisplayText() : "";
                    if (val.contains(String.valueOf(delimiter)) || val.contains("\"") || val.contains("\n")) {
                        val = "\"" + val.replace("\"", "\"\"") + "\"";
                    }
                    writer.write(val);
                    if (c < maxC) {
                        writer.write(delimiter);
                    }
                }
                writer.write("\n");
            }
        }
    }
}
