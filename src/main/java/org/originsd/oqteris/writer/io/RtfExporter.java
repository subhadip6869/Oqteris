package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced exporter for Rich Text Format (.rtf) documents with support for:
 * - Page breaks (\page)
 * - Paragraph formatting (alignment, space before/after, line spacing, indents)
 * - Text run styling (font, size, bold, italic, underline, strikethrough, color, highlight)
 * - Tables (row alignment, cell shading, cell padding, cell vertical alignment, borders)
 */
public class RtfExporter implements DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "RTF",
            "Rich Text Format (*.rtf)",
            DocumentType.WRITER,
            List.of("rtf"),
            "application/rtf",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new RtfExporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canExport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".rtf");
    }

    @Override
    public void exportDocument(WriterDocument document, File targetFile) throws IOException {
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        List<String> fontTable = new ArrayList<>();
        List<Color> colorTable = new ArrayList<>();
        colorTable.add(Color.BLACK); // Index 0 is auto/black

        // Collect fonts and colors from entire document
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) {
                collectParagraphStyles(p, fontTable, colorTable);
            } else if (el instanceof DocumentTable table) {
                if (table.getBorderColor() != null && !colorTable.contains(table.getBorderColor())) {
                    colorTable.add(table.getBorderColor());
                }
                for (DocumentTableRow row : table.getRows()) {
                    for (DocumentTableCell cell : row.getCells()) {
                        if (cell.getBackgroundColor() != null && !colorTable.contains(cell.getBackgroundColor())) {
                            colorTable.add(cell.getBackgroundColor());
                        }
                        for (Paragraph cp : cell.getParagraphs()) {
                            collectParagraphStyles(cp, fontTable, colorTable);
                        }
                    }
                }
            }
        }
        if (fontTable.isEmpty()) fontTable.add("Segoe UI");

        StringBuilder sb = new StringBuilder();
        // RTF Header
        sb.append("{\\rtf1\\ansi\\deff0\n");

        // Font Table
        sb.append("{\\fonttbl");
        for (int i = 0; i < fontTable.size(); i++) {
            sb.append("{\\f").append(i).append("\\fnil\\fcharset0 ").append(escapeRtf(fontTable.get(i))).append(";}");
        }
        sb.append("}\n");

        // Color Table
        sb.append("{\\colortbl ;");
        for (int i = 1; i < colorTable.size(); i++) {
            Color c = colorTable.get(i);
            int r = (int) Math.round(c.getRed() * 255);
            int g = (int) Math.round(c.getGreen() * 255);
            int b = (int) Math.round(c.getBlue() * 255);
            sb.append("\\red").append(r).append("\\green").append(g).append("\\blue").append(b).append(";");
        }
        sb.append("}\n");

        // Body Elements
        for (DocumentElement el : document.getElements()) {
            if (el instanceof PageBreakElement) {
                sb.append("\\page\n");
            } else if (el instanceof Paragraph p) {
                writeParagraph(p, sb, fontTable, colorTable);
            } else if (el instanceof DocumentTable table) {
                writeTable(table, sb, fontTable, colorTable);
            }
        }

        sb.append("}\n");

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.US_ASCII)) {
            writer.write(sb.toString());
        }
    }

    private void collectParagraphStyles(Paragraph p, List<String> fontTable, List<Color> colorTable) {
        for (TextRun run : p.getRuns()) {
            String font = run.getFontFamily();
            if (font != null && !fontTable.contains(font)) fontTable.add(font);
            Color c = run.getTextColor();
            if (c != null && !colorTable.contains(c)) colorTable.add(c);
            Color h = run.getHighlightColor();
            if (h != null && !colorTable.contains(h)) colorTable.add(h);
        }
    }

    private void writeParagraph(Paragraph p, StringBuilder sb, List<String> fontTable, List<Color> colorTable) {
        sb.append("\\pard");
        switch (p.getAlignment()) {
            case CENTER -> sb.append("\\qc");
            case RIGHT -> sb.append("\\qr");
            case JUSTIFY -> sb.append("\\qj");
            default -> sb.append("\\ql");
        }

        if (p.getSpaceBefore() > 0) {
            sb.append("\\sb").append((int) Math.round(p.getSpaceBefore() * 20.0));
        }
        if (p.getSpaceAfter() > 0) {
            sb.append("\\sa").append((int) Math.round(p.getSpaceAfter() * 20.0));
        }
        if (p.getLeftIndent() > 0) {
            sb.append("\\li").append((int) Math.round(p.getLeftIndent() * 20.0));
        }
        if (p.getFirstLineIndent() != 0) {
            sb.append("\\fi").append((int) Math.round(p.getFirstLineIndent() * 20.0));
        }
        if (p.getLineSpacing() > 0 && Math.abs(p.getLineSpacing() - 1.0) > 0.05) {
            sb.append("\\sl").append((int) Math.round(p.getLineSpacing() * 240.0)).append("\\slmult1");
        }
        sb.append(" ");

        for (TextRun run : p.getRuns()) {
            writeRun(run, sb, fontTable, colorTable);
        }
        sb.append("\\par\n");
    }

    private void writeRun(TextRun run, StringBuilder sb, List<String> fontTable, List<Color> colorTable) {
        int fontIdx = Math.max(0, fontTable.indexOf(run.getFontFamily()));
        int colorIdx = run.getTextColor() != null ? colorTable.indexOf(run.getTextColor()) : 0;
        int halfPoints = (int) Math.round(run.getFontSize() * 2);

        sb.append("{\\f").append(fontIdx).append("\\fs").append(halfPoints);
        if (colorIdx > 0) {
            sb.append("\\cf").append(colorIdx);
        }
        if (run.getHighlightColor() != null) {
            int hIdx = colorTable.indexOf(run.getHighlightColor());
            if (hIdx > 0) sb.append("\\highlight").append(hIdx);
        }
        if (run.isBold()) sb.append("\\b");
        if (run.isItalic()) sb.append("\\i");
        if (run.isUnderline()) sb.append("\\ul");
        if (run.isStrikethrough()) sb.append("\\strike");
        sb.append(" ");

        String raw = run.getText();
        if (raw != null) {
            sb.append(escapeRtf(raw));
        }
        sb.append("}");
    }

    private void writeTable(DocumentTable table, StringBuilder sb, List<String> fontTable, List<Color> colorTable) {
        int numCols = Math.max(1, table.getColumnCount());
        int cellWidthTwips = 8000 / numCols;

        for (DocumentTableRow row : table.getRows()) {
            sb.append("\\trowd\\trgaph108\\trleft0");
            switch (table.getAlignment()) {
                case CENTER -> sb.append("\\trqc");
                case RIGHT -> sb.append("\\trqr");
                default -> sb.append("\\trql");
            }

            int currentRight = 0;
            for (DocumentTableCell cell : row.getCells()) {
                int span = Math.max(1, cell.getColSpan());
                currentRight += cellWidthTwips * span;

                if (cell.getBackgroundColor() != null) {
                    int cIdx = colorTable.indexOf(cell.getBackgroundColor());
                    if (cIdx > 0) sb.append("\\clcbpat").append(cIdx);
                }

                switch (cell.getVerticalAlignment()) {
                    case CENTER -> sb.append("\\clvertalc");
                    case BOTTOM -> sb.append("\\clvertalb");
                    default -> sb.append("\\clvertalt");
                }

                sb.append("\\clpadt").append((int) Math.round(cell.getPaddingTop() * 20.0));
                sb.append("\\clpadb").append((int) Math.round(cell.getPaddingBottom() * 20.0));
                sb.append("\\clpadl").append((int) Math.round(cell.getPaddingLeft() * 20.0));
                sb.append("\\clpadr").append((int) Math.round(cell.getPaddingRight() * 20.0));

                if (!cell.isBorderTop()) sb.append("\\clbrdrt\\brdrnone");
                if (!cell.isBorderBottom()) sb.append("\\clbrdrb\\brdrnone");
                if (!cell.isBorderLeft()) sb.append("\\clbrdrl\\brdrnone");
                if (!cell.isBorderRight()) sb.append("\\clbrdrr\\brdrnone");

                sb.append("\\cellx").append(currentRight);
            }
            sb.append("\n");

            // Write row cells
            for (DocumentTableCell cell : row.getCells()) {
                sb.append("\\intbl ");
                for (int i = 0; i < cell.getParagraphs().size(); i++) {
                    Paragraph cp = cell.getParagraphs().get(i);
                    for (TextRun run : cp.getRuns()) {
                        writeRun(run, sb, fontTable, colorTable);
                    }
                    if (i < cell.getParagraphs().size() - 1) {
                        sb.append("\\par ");
                    }
                }
                sb.append("\\cell\n");
            }
            sb.append("\\row\n");
        }
    }

    private static String escapeRtf(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '{') sb.append("\\{");
            else if (c == '}') sb.append("\\}");
            else if (c == '\t') sb.append("\\tab ");
            else if (c == '\n') sb.append("\\line ");
            else if (c < 128) sb.append(c);
            else sb.append(String.format("\\u%d?", (int) c));
        }
        return sb.toString();
    }
}
