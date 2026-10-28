package org.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.DocumentTable;
import org.oqteris.writer.model.DocumentTableCell;
import org.oqteris.writer.model.DocumentTableRow;
import org.oqteris.writer.model.Paragraph;
import org.oqteris.writer.model.TextRun;
import org.oqteris.writer.model.WriterDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Importer for OpenDocument Text (.odt) documents.
 */
public class OdtImporter implements DocumentImporter<WriterDocument> {
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
        DocumentRegistry.getInstance().registerImporter(new OdtImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".odt");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        try (FileInputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("content.xml".equalsIgnoreCase(entry.getName())) {
                    parseContentXml(zis, document);
                    break;
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse ODT document: " + e.getMessage(), e);
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private void parseContentXml(InputStream is, WriterDocument document) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(is);

        NodeList textNodes = dom.getElementsByTagNameNS("*", "body");
        if (textNodes.getLength() == 0) return;

        Element body = (Element) textNodes.item(0);
        parseContainer(body, document);
    }

    private void parseContainer(Element container, WriterDocument document) {
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element el) {
                String localName = el.getLocalName();
                if ("h".equals(localName)) {
                    Paragraph p = parseParagraph(el);
                    String outlineLevel = el.getAttributeNS("*", "outline-level");
                    if ("1".equals(outlineLevel)) p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
                    else if ("2".equals(outlineLevel)) p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
                    else p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
                    document.addElement(p);
                } else if ("p".equals(localName)) {
                    document.addElement(parseParagraph(el));
                } else if ("table".equals(localName)) {
                    document.addElement(parseTable(el));
                } else {
                    parseContainer(el, document);
                }
            }
        }
    }

    private Paragraph parseParagraph(Element pElem) {
        Paragraph paragraph = new Paragraph();
        NodeList children = pElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (!text.isEmpty()) {
                    paragraph.addRun(new TextRun(text));
                }
            } else if (child instanceof Element childEl) {
                String localName = childEl.getLocalName();
                if ("span".equals(localName) || "a".equals(localName)) {
                    String text = childEl.getTextContent();
                    if (!text.isEmpty()) {
                        paragraph.addRun(new TextRun(text));
                    }
                }
            }
        }
        if (paragraph.getRuns().isEmpty()) {
            paragraph.addRun(new TextRun(""));
        }
        return paragraph;
    }

    private DocumentTable parseTable(Element tableElem) {
        NodeList rowNodes = tableElem.getElementsByTagNameNS("*", "table-row");
        int numRows = rowNodes.getLength();
        DocumentTable table = new DocumentTable();
        table.getRows().clear();

        for (int r = 0; r < numRows; r++) {
            Element rowEl = (Element) rowNodes.item(r);
            DocumentTableRow row = new DocumentTableRow();
            NodeList cellNodes = rowEl.getElementsByTagNameNS("*", "table-cell");
            for (int c = 0; c < cellNodes.getLength(); c++) {
                Element cellEl = (Element) cellNodes.item(c);
                DocumentTableCell cell = new DocumentTableCell();
                NodeList pNodes = cellEl.getElementsByTagNameNS("*", "p");
                if (pNodes.getLength() > 0) {
                    for (int p = 0; p < pNodes.getLength(); p++) {
                        cell.addParagraph(parseParagraph((Element) pNodes.item(p)));
                    }
                } else {
                    cell.addParagraph(new Paragraph(cellEl.getTextContent()));
                }
                row.addCell(cell);
            }
            table.addRow(row);
        }
        return table;
    }
}
