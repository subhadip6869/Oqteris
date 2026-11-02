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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporter for OpenDocument Text (.odt) documents with full fidelity for:
 * - Page breaks (PageBreakElement)
 * - Paragraph formatting (alignment, margins, line spacing, heading levels, indents)
 * - Text run styling (font family, size, bold, italic, underline, strikethrough, textColor, highlightColor)
 * - Tables with alignment, border styles, cell shading, cell padding, vertical alignment, and colSpan.
 */
public class OdtExporter implements DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "ODT",
            "OpenDocument Text (*.odt)",
            DocumentType.WRITER,
            List.of("odt"),
            "application/vnd.oasis.opendocument.text",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new OdtExporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canExport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".odt");
    }

    @Override
    public void exportDocument(WriterDocument document, File targetFile) throws IOException {
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. mimetype (must be uncompressed first entry in ODF)
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCrc(calculateCrc(mimeBytes));
            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            // 2. META-INF/manifest.xml
            zos.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            String manifest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">\n" +
                    "  <manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>\n" +
                    "  <manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>\n" +
                    "</manifest:manifest>";
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. content.xml
            zos.putNextEntry(new ZipEntry("content.xml"));
            String contentXml = buildContentXml(document);
            zos.write(contentXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private String buildContentXml(WriterDocument document) {
        StringBuilder automaticStyles = new StringBuilder();
        StringBuilder bodyContent = new StringBuilder();

        int styleCounter = 1;

        // Base page break style
        automaticStyles.append("    <style:style style:name=\"P_Break\" style:family=\"paragraph\">\n")
                .append("      <style:paragraph-properties fo:break-before=\"page\"/>\n")
                .append("    </style:style>\n");

        for (DocumentElement el : document.getElements()) {
            if (el instanceof PageBreakElement) {
                bodyContent.append("      <text:p text:style-name=\"P_Break\"/>\n");
            } else if (el instanceof Paragraph p) {
                writeParagraph(p, automaticStyles, bodyContent, "P_" + (styleCounter++));
            } else if (el instanceof DocumentTable table) {
                writeTable(table, automaticStyles, bodyContent, styleCounter++);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ");
        sb.append("xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" ");
        sb.append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" ");
        sb.append("xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" ");
        sb.append("xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\">\n");
        sb.append("  <office:automatic-styles>\n");
        sb.append(automaticStyles);
        sb.append("  </office:automatic-styles>\n");
        sb.append("  <office:body>\n");
        sb.append("    <office:text>\n");
        sb.append(bodyContent);
        sb.append("    </office:text>\n");
        sb.append("  </office:body>\n");
        sb.append("</office:document-content>");
        return sb.toString();
    }

    private void writeParagraph(Paragraph p, StringBuilder autoStyles, StringBuilder body, String styleName) {
        // Build paragraph style
        StringBuilder pProps = new StringBuilder();
        String align = switch (p.getAlignment()) {
            case CENTER -> "center";
            case RIGHT -> "end";
            case JUSTIFY -> "justify";
            default -> "start";
        };
        pProps.append(" fo:text-align=\"").append(align).append("\"");

        if (p.getSpaceBefore() > 0) {
            pProps.append(String.format(Locale.ROOT, " fo:margin-top=\"%.2fpt\"", p.getSpaceBefore()));
        }
        if (p.getSpaceAfter() > 0) {
            pProps.append(String.format(Locale.ROOT, " fo:margin-bottom=\"%.2fpt\"", p.getSpaceAfter()));
        }
        if (p.getLeftIndent() > 0) {
            pProps.append(String.format(Locale.ROOT, " fo:margin-left=\"%.2fpt\"", p.getLeftIndent()));
        }
        if (p.getFirstLineIndent() != 0) {
            pProps.append(String.format(Locale.ROOT, " fo:text-indent=\"%.2fpt\"", p.getFirstLineIndent()));
        }
        if (p.getLineSpacing() > 0 && Math.abs(p.getLineSpacing() - 1.0) > 0.05) {
            pProps.append(String.format(Locale.ROOT, " fo:line-height=\"%d%%\"", (int) Math.round(p.getLineSpacing() * 100)));
        }

        autoStyles.append("    <style:style style:name=\"").append(styleName).append("\" style:family=\"paragraph\">\n")
                .append("      <style:paragraph-properties").append(pProps).append("/>\n")
                .append("    </style:style>\n");

        boolean isHeading = p.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL;
        if (isHeading) {
            int level = switch (p.getHeadingStyle()) {
                case TITLE, HEADING_1 -> 1;
                case HEADING_2 -> 2;
                case HEADING_3 -> 3;
                default -> 1;
            };
            body.append("      <text:h text:style-name=\"").append(styleName).append("\" text:outline-level=\"").append(level).append("\">");
        } else {
            body.append("      <text:p text:style-name=\"").append(styleName).append("\">");
        }

        int runIdx = 1;
        for (TextRun run : p.getRuns()) {
            String textStyleName = styleName + "_T" + (runIdx++);
            writeTextRun(run, autoStyles, body, textStyleName);
        }

        if (isHeading) {
            body.append("</text:h>\n");
        } else {
            body.append("</text:p>\n");
        }
    }

    private void writeTextRun(TextRun run, StringBuilder autoStyles, StringBuilder body, String styleName) {
        StringBuilder tProps = new StringBuilder();
        if (run.getFontFamily() != null) {
            tProps.append(" fo:font-family=\"").append(escapeXml(run.getFontFamily())).append("\"");
        }
        if (run.getFontSize() > 0) {
            tProps.append(String.format(Locale.ROOT, " fo:font-size=\"%.1fpt\"", run.getFontSize()));
        }
        if (run.isBold()) {
            tProps.append(" fo:font-weight=\"bold\"");
        }
        if (run.isItalic()) {
            tProps.append(" fo:font-style=\"italic\"");
        }
        if (run.isUnderline()) {
            tProps.append(" style:text-underline-style=\"solid\"");
        }
        if (run.isStrikethrough()) {
            tProps.append(" style:text-line-through-style=\"solid\"");
        }
        if (run.getTextColor() != null) {
            tProps.append(" fo:color=\"").append(toHex(run.getTextColor())).append("\"");
        }
        if (run.getHighlightColor() != null) {
            tProps.append(" fo:background-color=\"").append(toHex(run.getHighlightColor())).append("\"");
        }

        autoStyles.append("    <style:style style:name=\"").append(styleName).append("\" style:family=\"text\">\n")
                .append("      <style:text-properties").append(tProps).append("/>\n")
                .append("    </style:style>\n");

        body.append("<text:span text:style-name=\"").append(styleName).append("\">");
        // Handle tabs and newlines
        String raw = run.getText();
        if (raw != null) {
            String escaped = escapeXml(raw)
                    .replace("\t", "<text:tab/>")
                    .replace("\n", "<text:line-break/>");
            body.append(escaped);
        }
        body.append("</text:span>");
    }

    private void writeTable(DocumentTable table, StringBuilder autoStyles, StringBuilder body, int tableId) {
        String tblStyleName = "Table_" + tableId;
        String align = switch (table.getAlignment()) {
            case CENTER -> "center";
            case RIGHT -> "right";
            default -> "left";
        };

        String borderStr = "";
        if (table.getBorderWidth() > 0) {
            borderStr = String.format(Locale.ROOT, " fo:border=\"%.2fpt solid %s\"",
                    table.getBorderWidth(), table.getBorderColor() != null ? toHex(table.getBorderColor()) : "#000000");
        } else {
            borderStr = " fo:border=\"none\"";
        }

        autoStyles.append("    <style:style style:name=\"").append(tblStyleName).append("\" style:family=\"table\">\n")
                .append("      <style:table-properties table:align=\"").append(align).append("\"").append(borderStr).append("/>\n")
                .append("    </style:style>\n");

        int numCols = Math.max(1, table.getColumnCount());
        String colStyleName = "Col_" + tableId;
        double colW = 468.0 / numCols;
        autoStyles.append("    <style:style style:name=\"").append(colStyleName).append("\" style:family=\"table-column\">\n")
                .append(String.format(Locale.ROOT, "      <style:table-column-properties style:column-width=\"%.2fpt\"/>\n", colW))
                .append("    </style:style>\n");

        body.append("      <table:table table:style-name=\"").append(tblStyleName).append("\">\n");

        for (int c = 0; c < numCols; c++) {
            body.append("        <table:table-column table:style-name=\"").append(colStyleName).append("\"/>\n");
        }

        int cellCounter = 1;
        for (DocumentTableRow row : table.getRows()) {
            body.append("        <table:table-row>\n");
            for (DocumentTableCell cell : row.getCells()) {
                String cellStyleName = "Cell_" + tableId + "_" + (cellCounter++);
                StringBuilder cellProps = new StringBuilder();
                if (cell.getBackgroundColor() != null) {
                    cellProps.append(" fo:background-color=\"").append(toHex(cell.getBackgroundColor())).append("\"");
                }
                String va = switch (cell.getVerticalAlignment()) {
                    case CENTER -> "middle";
                    case BOTTOM -> "bottom";
                    default -> "top";
                };
                cellProps.append(" style:vertical-align=\"").append(va).append("\"");

                cellProps.append(String.format(Locale.ROOT,
                        " fo:padding-top=\"%.2fpt\" fo:padding-bottom=\"%.2fpt\" fo:padding-left=\"%.2fpt\" fo:padding-right=\"%.2fpt\"",
                        cell.getPaddingTop(), cell.getPaddingBottom(), cell.getPaddingLeft(), cell.getPaddingRight()));

                if (!cell.isBorderTop()) cellProps.append(" fo:border-top=\"none\"");
                if (!cell.isBorderBottom()) cellProps.append(" fo:border-bottom=\"none\"");
                if (!cell.isBorderLeft()) cellProps.append(" fo:border-left=\"none\"");
                if (!cell.isBorderRight()) cellProps.append(" fo:border-right=\"none\"");

                autoStyles.append("    <style:style style:name=\"").append(cellStyleName).append("\" style:family=\"table-cell\">\n")
                        .append("      <style:table-cell-properties").append(cellProps).append("/>\n")
                        .append("    </style:style>\n");

                body.append("          <table:table-cell table:style-name=\"").append(cellStyleName).append("\"");
                if (cell.getColSpan() > 1) {
                    body.append(" table:number-columns-spanned=\"").append(cell.getColSpan()).append("\"");
                }
                body.append(" office:value-type=\"string\">\n");

                for (Paragraph cp : cell.getParagraphs()) {
                    writeParagraph(cp, autoStyles, body, cellStyleName + "_P" + (cellCounter++));
                }

                body.append("          </table:table-cell>\n");
            }
            body.append("        </table:table-row>\n");
        }
        body.append("      </table:table>\n");
    }

    private static String toHex(Color c) {
        if (c == null) return "#000000";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static long calculateCrc(byte[] bytes) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
