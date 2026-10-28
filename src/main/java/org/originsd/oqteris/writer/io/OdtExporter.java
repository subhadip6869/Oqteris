package org.originsd.oqteris.writer.io;

import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporter for OpenDocument Text (.odt) documents.
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
            StringBuilder content = new StringBuilder();
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            content.append("<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ");
            content.append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" ");
            content.append("xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\">\n");
            content.append("  <office:body>\n");
            content.append("    <office:text>\n");

            for (DocumentElement el : document.getElements()) {
                if (el instanceof Paragraph p) {
                    if (p.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL) {
                        int level = switch (p.getHeadingStyle()) {
                            case TITLE, HEADING_1 -> 1;
                            case HEADING_2 -> 2;
                            case HEADING_3 -> 3;
                            default -> 1;
                        };
                        content.append("      <text:h text:outline-level=\"").append(level).append("\">");
                    } else {
                        content.append("      <text:p>");
                    }

                    for (TextRun run : p.getRuns()) {
                        content.append(escapeXml(run.getText()));
                    }

                    if (p.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL) {
                        content.append("</text:h>\n");
                    } else {
                        content.append("</text:p>\n");
                    }

                } else if (el instanceof DocumentTable table) {
                    content.append("      <table:table>\n");
                    for (DocumentTableRow row : table.getRows()) {
                        content.append("        <table:table-row>\n");
                        for (DocumentTableCell cell : row.getCells()) {
                            content.append("          <table:table-cell office:value-type=\"string\">\n");
                            for (Paragraph cp : cell.getParagraphs()) {
                                content.append("            <text:p>").append(escapeXml(cp.getPlainText())).append("</text:p>\n");
                            }
                            content.append("          </table:table-cell>\n");
                        }
                        content.append("        </table:table-row>\n");
                    }
                    content.append("      </table:table>\n");
                }
            }

            content.append("    </office:text>\n");
            content.append("  </office:body>\n");
            content.append("</office:document-content>");

            zos.write(content.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
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
