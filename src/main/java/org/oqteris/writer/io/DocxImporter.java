package org.oqteris.writer.io;

import org.apache.poi.xwpf.usermodel.*;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;
import org.oqteris.writer.model.Paragraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for Office Open XML Word Documents (.docx).
 */
public class DocxImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "DOCX",
            "Word Document (*.docx)",
            DocumentType.WRITER,
            List.of("docx", "docm"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new DocxImporter());
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
        return name.endsWith(".docx") || name.endsWith(".docm");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        try (FileInputStream in = new FileInputStream(file);
             XWPFDocument docx = new XWPFDocument(in)) {

            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    Paragraph para = convertParagraph(p);
                    document.addElement(para);
                } else if (element instanceof XWPFTable t) {
                    DocumentTable table = convertTable(t);
                    document.addElement(table);
                }
            }
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private Paragraph convertParagraph(XWPFParagraph poiP) {
        Paragraph para = new Paragraph();

        // Alignment
        if (poiP.getAlignment() != null) {
            switch (poiP.getAlignment()) {
                case CENTER -> para.setAlignment(Paragraph.Alignment.CENTER);
                case RIGHT -> para.setAlignment(Paragraph.Alignment.RIGHT);
                case BOTH -> para.setAlignment(Paragraph.Alignment.JUSTIFY);
                default -> para.setAlignment(Paragraph.Alignment.LEFT);
            }
        }

        // Heading style
        String style = poiP.getStyle();
        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.contains("title")) para.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
            else if (lower.contains("heading 1") || lower.contains("1")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            else if (lower.contains("heading 2") || lower.contains("2")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            else if (lower.contains("heading 3") || lower.contains("3")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
        }

        // Runs
        for (XWPFRun poiRun : poiP.getRuns()) {
            String text = poiRun.getText(0);
            if (text != null && !text.isEmpty()) {
                TextRun run = new TextRun(text);
                if (poiRun.getFontFamily() != null) run.setFontFamily(poiRun.getFontFamily());
                if (poiRun.getFontSize() > 0) run.setFontSize(poiRun.getFontSize());
                run.setBold(poiRun.isBold());
                run.setItalic(poiRun.isItalic());
                run.setUnderline(poiRun.getUnderline() != UnderlinePatterns.NONE);
                run.setStrikethrough(poiRun.isStrikeThrough());

                // Color
                String colorHex = poiRun.getColor();
                if (colorHex != null && colorHex.length() == 6) {
                    try {
                        run.setTextColor(javafx.scene.paint.Color.web("#" + colorHex));
                    } catch (Exception ignored) {
                    }
                }

                para.addRun(run);
            }
        }

        return para;
    }

    private DocumentTable convertTable(XWPFTable poiTable) {
        DocumentTable table = new DocumentTable();

        for (XWPFTableRow poiRow : poiTable.getRows()) {
            DocumentTableRow row = new DocumentTableRow();
            for (XWPFTableCell poiCell : poiRow.getTableCells()) {
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();

                for (XWPFParagraph p : poiCell.getParagraphs()) {
                    cell.addParagraph(convertParagraph(p));
                }
                if (cell.getParagraphs().isEmpty()) {
                    cell.addParagraph(new Paragraph());
                }
                row.addCell(cell);
            }
            table.addRow(row);
        }

        return table;
    }
}
