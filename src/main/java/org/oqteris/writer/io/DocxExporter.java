package org.oqteris.writer.io;

import org.apache.poi.xwpf.usermodel.*;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;
import org.oqteris.writer.model.Paragraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exporter for Office Open XML Word Documents (.docx).
 */
public class DocxExporter implements DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "DOCX",
            "Word Document (*.docx)",
            DocumentType.WRITER,
            List.of("docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new DocxExporter());
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
        return file.getName().toLowerCase().endsWith(".docx");
    }

    @Override
    public void exportDocument(WriterDocument document, File file) throws IOException {
        try (XWPFDocument docx = new XWPFDocument()) {

            for (DocumentElement element : document.getElements()) {
                if (element instanceof Paragraph p) {
                    XWPFParagraph poiP = docx.createParagraph();
                    writeParagraph(p, poiP);
                } else if (element instanceof DocumentTable table) {
                    writeTable(table, docx);
                } else if (element instanceof PageBreakElement) {
                    XWPFParagraph p = docx.createParagraph();
                    p.setPageBreak(true);
                }
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                docx.write(out);
            }
        }
    }

    private void writeParagraph(Paragraph p, XWPFParagraph poiP) {
        switch (p.getAlignment()) {
            case CENTER -> poiP.setAlignment(ParagraphAlignment.CENTER);
            case RIGHT -> poiP.setAlignment(ParagraphAlignment.RIGHT);
            case JUSTIFY -> poiP.setAlignment(ParagraphAlignment.BOTH);
            default -> poiP.setAlignment(ParagraphAlignment.LEFT);
        }

        switch (p.getHeadingStyle()) {
            case TITLE -> poiP.setStyle("Title");
            case HEADING_1 -> poiP.setStyle("Heading1");
            case HEADING_2 -> poiP.setStyle("Heading2");
            case HEADING_3 -> poiP.setStyle("Heading3");
            default -> {
            }
        }

        for (TextRun run : p.getRuns()) {
            XWPFRun poiRun = poiP.createRun();
            poiRun.setText(run.getText());
            poiRun.setFontFamily(run.getFontFamily());
            poiRun.setFontSize((int) run.getFontSize());
            poiRun.setBold(run.isBold());
            poiRun.setItalic(run.isItalic());
            if (run.isUnderline()) poiRun.setUnderline(UnderlinePatterns.SINGLE);
            poiRun.setStrikeThrough(run.isStrikethrough());

            if (run.getTextColor() != null) {
                int r = (int) Math.round(run.getTextColor().getRed() * 255);
                int g = (int) Math.round(run.getTextColor().getGreen() * 255);
                int b = (int) Math.round(run.getTextColor().getBlue() * 255);
                poiRun.setColor(String.format("%02X%02X%02X", r, g, b));
            }
        }
    }

    private void writeTable(DocumentTable table, XWPFDocument docx) {
        if (table.getRows().isEmpty()) return;
        int numRows = table.getRows().size();
        int numCols = table.getColumnCount();

        XWPFTable poiTable = docx.createTable(numRows, numCols);

        for (int r = 0; r < numRows; r++) {
            DocumentTableRow row = table.getRows().get(r);
            XWPFTableRow poiRow = poiTable.getRow(r);

            for (int c = 0; c < Math.min(numCols, row.getCells().size()); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                XWPFTableCell poiCell = poiRow.getCell(c);

                // Clear default paragraph in POI cell
                if (!cell.getParagraphs().isEmpty()) {
                    for (int i = 0; i < cell.getParagraphs().size(); i++) {
                        Paragraph p = cell.getParagraphs().get(i);
                        XWPFParagraph poiP = (i == 0 && poiCell.getParagraphs().size() > 0) ?
                                poiCell.getParagraphs().get(0) : poiCell.addParagraph();
                        writeParagraph(p, poiP);
                    }
                }
            }
        }
    }
}
