package org.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporter for Rich Text Format (.rtf) documents.
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
        colorTable.add(Color.BLACK); // Index 0

        // Collect fonts and colors
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) {
                for (TextRun run : p.getRuns()) {
                    String font = run.getFontFamily();
                    if (!fontTable.contains(font)) fontTable.add(font);
                    Color c = run.getTextColor();
                    if (c != null && !colorTable.contains(c)) colorTable.add(c);
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
        for (Color c : colorTable) {
            int r = (int) Math.round(c.getRed() * 255);
            int g = (int) Math.round(c.getGreen() * 255);
            int b = (int) Math.round(c.getBlue() * 255);
            sb.append("\\red").append(r).append("\\green").append(g).append("\\blue").append(b).append(";");
        }
        sb.append("}\n");

        // Body Elements
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) {
                sb.append("\\pard");
                switch (p.getAlignment()) {
                    case CENTER -> sb.append("\\qc");
                    case RIGHT -> sb.append("\\qr");
                    case JUSTIFY -> sb.append("\\qj");
                    default -> sb.append("\\ql");
                }
                sb.append(" ");

                for (TextRun run : p.getRuns()) {
                    int fontIdx = Math.max(0, fontTable.indexOf(run.getFontFamily()));
                    int colorIdx = Math.max(0, colorTable.indexOf(run.getTextColor())) + 1;
                    int halfPoints = (int) Math.round(run.getFontSize() * 2);

                    sb.append("{\\f").append(fontIdx).append("\\fs").append(halfPoints);
                    sb.append("\\cf").append(colorIdx);
                    if (run.isBold()) sb.append("\\b");
                    if (run.isItalic()) sb.append("\\i");
                    if (run.isUnderline()) sb.append("\\ul");
                    if (run.isStrikethrough()) sb.append("\\strike");
                    sb.append(" ");
                    sb.append(escapeRtf(run.getText()));
                    sb.append("}");
                }
                sb.append("\\par\n");

            } else if (el instanceof DocumentTable table) {
                for (DocumentTableRow row : table.getRows()) {
                    sb.append("\\trowd\\trgaph108\\trleft0\n");
                    int cellRight = 2000;
                    for (int c = 0; c < row.getCells().size(); c++) {
                        sb.append("\\cellx").append(cellRight).append("\n");
                        cellRight += 2000;
                    }
                    for (DocumentTableCell cell : row.getCells()) {
                        sb.append("\\intbl ");
                        for (Paragraph p : cell.getParagraphs()) {
                            sb.append(escapeRtf(p.getPlainText())).append(" ");
                        }
                        sb.append("\\cell\n");
                    }
                    sb.append("\\row\n");
                }

            } else if (el instanceof PageBreakElement) {
                sb.append("\\page\n");
            }
        }

        sb.append("}\n");

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.US_ASCII)) {
            writer.write(sb.toString());
        }
    }

    private String escapeRtf(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch == '\\') out.append("\\\\");
            else if (ch == '{') out.append("\\{");
            else if (ch == '}') out.append("\\}");
            else if (ch > 127) {
                out.append("\\u").append((int) ch).append("?");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
