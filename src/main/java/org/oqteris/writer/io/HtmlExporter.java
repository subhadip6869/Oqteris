package org.oqteris.writer.io;

import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;
import org.oqteris.writer.model.Paragraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exporter converting WriterDocument to styled HTML.
 */
public class HtmlExporter implements DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "HTML",
            "HTML Document (*.html, *.htm)",
            DocumentType.WRITER,
            List.of("html", "htm"),
            "text/html",
            false,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new HtmlExporter());
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
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public void exportDocument(WriterDocument document, File file) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
            w.write("<title>" + escapeHtml(document.getTitle()) + "</title>\n");
            w.write("<style>\n");
            w.write("body { font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; line-height: 1.5; margin: 40px auto; max-width: 800px; color: #222; }\n");
            w.write("table { border-collapse: collapse; width: 100%; margin: 16px 0; }\n");
            w.write("th, td { border: 1px solid #ccc; padding: 6px 10px; }\n");
            w.write("</style>\n</head>\n<body>\n");

            for (DocumentElement el : document.getElements()) {
                if (el instanceof Paragraph p) {
                    writeParagraphHtml(p, w);
                } else if (el instanceof DocumentTable t) {
                    writeTableHtml(t, w);
                } else if (el instanceof PageBreakElement) {
                    w.write("<hr style=\"page-break-after: always;\" />\n");
                }
            }

            w.write("</body>\n</html>");
        }
    }

    private void writeParagraphHtml(Paragraph p, BufferedWriter w) throws IOException {
        String tag = switch (p.getHeadingStyle()) {
            case TITLE -> "h1";
            case HEADING_1 -> "h2";
            case HEADING_2 -> "h3";
            case HEADING_3 -> "h4";
            default -> "p";
        };

        String align = switch (p.getAlignment()) {
            case CENTER -> "text-align: center;";
            case RIGHT -> "text-align: right;";
            case JUSTIFY -> "text-align: justify;";
            default -> "text-align: left;";
        };

        w.write("<" + tag + " style=\"" + align + "\">");
        for (TextRun run : p.getRuns()) {
            StringBuilder runHtml = new StringBuilder();
            if (run.isBold()) runHtml.append("<strong>");
            if (run.isItalic()) runHtml.append("<em>");
            if (run.isUnderline()) runHtml.append("<u>");
            if (run.isStrikethrough()) runHtml.append("<s>");

            runHtml.append(escapeHtml(run.getText()));

            if (run.isStrikethrough()) runHtml.append("</s>");
            if (run.isUnderline()) runHtml.append("</u>");
            if (run.isItalic()) runHtml.append("</em>");
            if (run.isBold()) runHtml.append("</strong>");

            w.write(runHtml.toString());
        }
        w.write("</" + tag + ">\n");
    }

    private void writeTableHtml(DocumentTable t, BufferedWriter w) throws IOException {
        w.write("<table>\n");
        for (DocumentTableRow row : t.getRows()) {
            w.write("<tr>\n");
            for (DocumentTableCell cell : row.getCells()) {
                w.write("<td>\n");
                for (Paragraph p : cell.getParagraphs()) {
                    writeParagraphHtml(p, w);
                }
                w.write("</td>\n");
            }
            w.write("</tr>\n");
        }
        w.write("</table>\n");
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }
}
