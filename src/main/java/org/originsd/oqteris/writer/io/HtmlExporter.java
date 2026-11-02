package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Enhanced exporter converting WriterDocument to styled, responsive HTML.
 * Supports:
 * - Page breaks (<div style="page-break-before: always;">)
 * - Paragraph formatting (alignment, margins, line-height, text-indent, heading styles, lists)
 * - Text styling (font-family, font-size, bold, italic, underline, strike, textColor, highlightColor)
 * - Tables (alignment, borders, cell shading, cell padding, vertical-align, colSpan)
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
        String defFont = document.getDetectedFontFamily();
        if (defFont == null || defFont.isBlank()) defFont = "Segoe UI";
        double defSize = document.getDefaultFontSize() > 0 ? document.getDefaultFontSize() : 11.0;

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
            w.write("<title>" + escapeHtml(document.getTitle()) + "</title>\n");
            w.write("<style>\n");
            w.write("body { font-family: " + defFont + ", -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; font-size: " + defSize + "pt; line-height: 1.5; margin: 40px auto; max-width: 800px; color: #111; }\n");
            w.write("table { border-collapse: collapse; margin: 16px 0; }\n");
            w.write("th, td { border: 1px solid #ccc; padding: 6px 10px; }\n");
            w.write(".pagebreak { page-break-before: always; margin-top: 30px; border-top: 1px dashed #aaa; }\n");
            w.write("</style>\n</head>\n<body>\n");

            for (DocumentElement el : document.getElements()) {
                if (el instanceof PageBreakElement) {
                    w.write("<div class=\"pagebreak\" style=\"page-break-before: always;\"></div>\n");
                } else if (el instanceof Paragraph p) {
                    writeParagraphHtml(p, w);
                } else if (el instanceof DocumentTable t) {
                    writeTableHtml(t, w);
                }
            }

            w.write("</body>\n</html>\n");
        }
    }

    private void writeParagraphHtml(Paragraph p, BufferedWriter w) throws IOException {
        String tag = switch (p.getHeadingStyle()) {
            case TITLE -> "h1";
            case HEADING_1 -> "h2";
            case HEADING_2 -> "h3";
            case HEADING_3 -> "h4";
            default -> p.getListType() != Paragraph.ListType.NONE ? "li" : "p";
        };

        StringBuilder style = new StringBuilder();
        String align = switch (p.getAlignment()) {
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "justify";
            default -> "left";
        };
        style.append("text-align: ").append(align).append("; ");

        if (p.getSpaceBefore() > 0) {
            style.append(String.format(Locale.ROOT, "margin-top: %.1fpt; ", p.getSpaceBefore()));
        }
        if (p.getSpaceAfter() > 0) {
            style.append(String.format(Locale.ROOT, "margin-bottom: %.1fpt; ", p.getSpaceAfter()));
        }
        if (p.getLeftIndent() > 0) {
            style.append(String.format(Locale.ROOT, "margin-left: %.1fpt; ", p.getLeftIndent()));
        }
        if (p.getFirstLineIndent() != 0) {
            style.append(String.format(Locale.ROOT, "text-indent: %.1fpt; ", p.getFirstLineIndent()));
        }
        if (p.getLineSpacing() > 0 && Math.abs(p.getLineSpacing() - 1.0) > 0.05) {
            style.append(String.format(Locale.ROOT, "line-height: %.2f; ", p.getLineSpacing()));
        }

        if (p.getListType() == Paragraph.ListType.BULLET) {
            w.write("<ul>");
        } else if (p.getListType() == Paragraph.ListType.NUMBERED) {
            w.write("<ol>");
        }

        w.write("<" + tag + " style=\"" + style.toString().trim() + "\">");

        for (TextRun run : p.getRuns()) {
            writeRunHtml(run, w);
        }

        w.write("</" + tag + ">\n");

        if (p.getListType() == Paragraph.ListType.BULLET) {
            w.write("</ul>\n");
        } else if (p.getListType() == Paragraph.ListType.NUMBERED) {
            w.write("</ol>\n");
        }
    }

    private void writeRunHtml(TextRun run, BufferedWriter w) throws IOException {
        StringBuilder spanStyle = new StringBuilder();
        if (run.getFontFamily() != null) {
            spanStyle.append("font-family: ").append(escapeHtml(run.getFontFamily())).append("; ");
        }
        if (run.getFontSize() > 0) {
            spanStyle.append(String.format(Locale.ROOT, "font-size: %.1fpt; ", run.getFontSize()));
        }
        if (run.getTextColor() != null) {
            spanStyle.append("color: ").append(toHex(run.getTextColor())).append("; ");
        }
        if (run.getHighlightColor() != null) {
            spanStyle.append("background-color: ").append(toHex(run.getHighlightColor())).append("; ");
        }

        boolean hasSpan = spanStyle.length() > 0;
        if (hasSpan) {
            w.write("<span style=\"" + spanStyle.toString().trim() + "\">");
        }

        if (run.isBold()) w.write("<strong>");
        if (run.isItalic()) w.write("<em>");
        if (run.isUnderline()) w.write("<u>");
        if (run.isStrikethrough()) w.write("<s>");

        String txt = run.getText();
        if (txt != null) {
            w.write(escapeHtml(txt).replace("\n", "<br/>").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
        }

        if (run.isStrikethrough()) w.write("</s>");
        if (run.isUnderline()) w.write("</u>");
        if (run.isItalic()) w.write("</em>");
        if (run.isBold()) w.write("</strong>");

        if (hasSpan) {
            w.write("</span>");
        }
    }

    private void writeTableHtml(DocumentTable t, BufferedWriter w) throws IOException {
        StringBuilder tblStyle = new StringBuilder("border-collapse: collapse; width: 100%; ");
        switch (t.getAlignment()) {
            case CENTER -> tblStyle.append("margin-left: auto; margin-right: auto; ");
            case RIGHT -> tblStyle.append("margin-left: auto; margin-right: 0; ");
            default -> tblStyle.append("margin-left: 0; margin-right: auto; ");
        }

        if (t.getBorderWidth() > 0) {
            String col = t.getBorderColor() != null ? toHex(t.getBorderColor()) : "#ccc";
            tblStyle.append(String.format(Locale.ROOT, "border: %.1fpt solid %s; ", t.getBorderWidth(), col));
        } else {
            tblStyle.append("border: none; ");
        }

        w.write("<table style=\"" + tblStyle.toString().trim() + "\">\n");

        for (DocumentTableRow row : t.getRows()) {
            w.write("  <tr>\n");
            for (DocumentTableCell cell : row.getCells()) {
                StringBuilder cellStyle = new StringBuilder();

                if (cell.getBackgroundColor() != null) {
                    cellStyle.append("background-color: ").append(toHex(cell.getBackgroundColor())).append("; ");
                }

                String va = switch (cell.getVerticalAlignment()) {
                    case CENTER -> "middle";
                    case BOTTOM -> "bottom";
                    default -> "top";
                };
                cellStyle.append("vertical-align: ").append(va).append("; ");

                cellStyle.append(String.format(Locale.ROOT,
                        "padding: %.1fpt %.1fpt %.1fpt %.1fpt; ",
                        cell.getPaddingTop(), cell.getPaddingRight(), cell.getPaddingBottom(), cell.getPaddingLeft()));

                if (!cell.isBorderTop()) cellStyle.append("border-top: none; ");
                if (!cell.isBorderBottom()) cellStyle.append("border-bottom: none; ");
                if (!cell.isBorderLeft()) cellStyle.append("border-left: none; ");
                if (!cell.isBorderRight()) cellStyle.append("border-right: none; ");

                w.write("    <td");
                if (cell.getColSpan() > 1) {
                    w.write(" colspan=\"" + cell.getColSpan() + "\"");
                }
                w.write(" style=\"" + cellStyle.toString().trim() + "\">\n");

                for (Paragraph p : cell.getParagraphs()) {
                    writeParagraphHtml(p, w);
                }
                w.write("    </td>\n");
            }
            w.write("  </tr>\n");
        }
        w.write("</table>\n");
    }

    private static String toHex(Color c) {
        if (c == null) return "#000000";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
