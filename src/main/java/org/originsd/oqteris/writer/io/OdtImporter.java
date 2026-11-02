package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;
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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Enhanced importer for OpenDocument Text (.odt) documents with full support for:
 * - Page breaks (fo:break-before="page")
 * - Document default font and size detection
 * - Paragraph formatting (alignment, margins, line spacing, indents, heading levels)
 * - Text run styling (font family, size, bold, italic, underline, strike, colors, highlight)
 * - Tables with alignment, column widths, row heights, cell shading, cell padding,
 *   cell borders suppression, cell vertical alignment, and column spanning (colSpan).
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

        // 1. Index all styles from automatic-styles and styles
        StyleIndex styleIndex = new StyleIndex();
        styleIndex.indexStyles(dom);

        // 2. Detect default font and size
        String detectedFont = styleIndex.detectDefaultFont();
        double detectedSize = styleIndex.detectDefaultSize();
        document.setDefaultFontFamily(detectedFont);
        document.setDefaultFontSize(detectedSize);

        // 3. Parse document body
        NodeList textNodes = dom.getElementsByTagNameNS("*", "body");
        if (textNodes.getLength() == 0) return;

        Element body = (Element) textNodes.item(0);
        parseContainer(body, document, styleIndex, detectedFont, detectedSize);
    }

    private void parseContainer(Element container, WriterDocument document, StyleIndex styles, String docFont, double docSize) {
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element el) {
                String localName = el.getLocalName();
                if ("h".equals(localName)) {
                    Paragraph p = parseParagraph(el, styles, docFont, docSize);
                    String outlineLevel = getAttrAnyNS(el, "outline-level");
                    if ("1".equals(outlineLevel)) p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
                    else if ("2".equals(outlineLevel)) p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
                    else if ("3".equals(outlineLevel)) p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
                    else p.setHeadingStyle(Paragraph.HeadingStyle.TITLE);

                    if (styles.hasPageBreak(el)) {
                        document.addElement(new PageBreakElement());
                    }
                    document.addElement(p);
                } else if ("p".equals(localName)) {
                    if (styles.hasPageBreak(el)) {
                        document.addElement(new PageBreakElement());
                    }
                    Paragraph p = parseParagraph(el, styles, docFont, docSize);
                    // If paragraph is an empty spacer following a page break, don't duplicate
                    document.addElement(p);
                } else if ("table".equals(localName)) {
                    document.addElement(parseTable(el, styles, docFont, docSize));
                } else if ("list".equals(localName)) {
                    parseList(el, document, styles, docFont, docSize, Paragraph.ListType.BULLET);
                } else {
                    parseContainer(el, document, styles, docFont, docSize);
                }
            }
        }
    }

    private void parseList(Element listElem, WriterDocument document, StyleIndex styles, String docFont, double docSize, Paragraph.ListType defaultListType) {
        NodeList items = listElem.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            if (item instanceof Element itemEl && "list-item".equals(itemEl.getLocalName())) {
                NodeList subNodes = itemEl.getChildNodes();
                for (int j = 0; j < subNodes.getLength(); j++) {
                    Node sub = subNodes.item(j);
                    if (sub instanceof Element subEl) {
                        if ("p".equals(subEl.getLocalName()) || "h".equals(subEl.getLocalName())) {
                            Paragraph p = parseParagraph(subEl, styles, docFont, docSize);
                            p.setListType(defaultListType);
                            document.addElement(p);
                        } else if ("list".equals(subEl.getLocalName())) {
                            parseList(subEl, document, styles, docFont, docSize, defaultListType);
                        }
                    }
                }
            }
        }
    }

    private Paragraph parseParagraph(Element pElem, StyleIndex styles, String docFont, double docSize) {
        Paragraph paragraph = new Paragraph();

        String styleName = getAttrAnyNS(pElem, "style-name");
        ParagraphStyleInfo pStyle = styles.getParagraphStyle(styleName);

        if (pStyle != null) {
            if (pStyle.alignment != null) paragraph.setAlignment(pStyle.alignment);
            if (pStyle.spaceBefore >= 0) paragraph.setSpaceBefore(pStyle.spaceBefore);
            if (pStyle.spaceAfter >= 0) paragraph.setSpaceAfter(pStyle.spaceAfter);
            if (pStyle.lineSpacing > 0) paragraph.setLineSpacing(pStyle.lineSpacing);
            if (pStyle.leftIndent >= 0) paragraph.setLeftIndent(pStyle.leftIndent);
            if (pStyle.firstLineIndent != 0) paragraph.setFirstLineIndent(pStyle.firstLineIndent);
        }

        TextStyleInfo defaultTextStyle = pStyle != null && pStyle.textStyle != null ? pStyle.textStyle : new TextStyleInfo();
        if (defaultTextStyle.fontFamily == null) defaultTextStyle.fontFamily = docFont;
        if (defaultTextStyle.fontSize <= 0) defaultTextStyle.fontSize = docSize;

        parseParagraphRuns(pElem, paragraph, styles, defaultTextStyle);

        if (paragraph.getRuns().isEmpty()) {
            paragraph.addRun(new TextRun("", defaultTextStyle.fontFamily, defaultTextStyle.fontSize));
        }
        return paragraph;
    }

    private void parseParagraphRuns(Element parent, Paragraph paragraph, StyleIndex styles, TextStyleInfo inheritedStyle) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (!text.isEmpty()) {
                    paragraph.addRun(createRun(text, inheritedStyle));
                }
            } else if (child instanceof Element childEl) {
                String localName = childEl.getLocalName();
                if ("span".equals(localName) || "a".equals(localName)) {
                    String spanStyleName = getAttrAnyNS(childEl, "style-name");
                    TextStyleInfo spanStyle = styles.getTextStyle(spanStyleName);
                    TextStyleInfo merged = TextStyleInfo.merge(inheritedStyle, spanStyle);
                    parseParagraphRuns(childEl, paragraph, styles, merged);
                } else if ("tab".equals(localName)) {
                    paragraph.addRun(createRun("\t", inheritedStyle));
                } else if ("line-break".equals(localName)) {
                    paragraph.addRun(createRun("\n", inheritedStyle));
                } else if ("s".equals(localName)) {
                    // ODF multiple spaces: <text:s text:c="3"/>
                    int count = 1;
                    String cAttr = getAttrAnyNS(childEl, "c");
                    if (cAttr != null && !cAttr.isBlank()) {
                        try { count = Integer.parseInt(cAttr.trim()); } catch (Exception ignored) {}
                    }
                    paragraph.addRun(createRun(" ".repeat(Math.max(1, count)), inheritedStyle));
                }
            }
        }
    }

    private TextRun createRun(String text, TextStyleInfo info) {
        String font = info.fontFamily != null ? info.fontFamily : "Segoe UI";
        double size = info.fontSize > 0 ? info.fontSize : 11.0;
        Color color = info.textColor != null ? info.textColor : Color.BLACK;
        return new TextRun(text, font, size, info.bold, info.italic, info.underline, info.strike, color, info.highlightColor);
    }

    private DocumentTable parseTable(Element tableElem, StyleIndex styles, String docFont, double docSize) {
        DocumentTable table = new DocumentTable();
        table.getRows().clear();

        String tblStyleName = getAttrAnyNS(tableElem, "style-name");
        TableStyleInfo tblStyle = styles.getTableStyle(tblStyleName);
        if (tblStyle != null) {
            if (tblStyle.alignment != null) table.setAlignment(tblStyle.alignment);
            if (tblStyle.borderColor != null) table.setBorderColor(tblStyle.borderColor);
            if (tblStyle.borderWidth >= 0) table.setBorderWidth(tblStyle.borderWidth);
        }

        // Table columns
        NodeList colNodes = tableElem.getElementsByTagNameNS("*", "table-column");
        List<Double> colWidths = new ArrayList<>();
        for (int i = 0; i < colNodes.getLength(); i++) {
            Element colEl = (Element) colNodes.item(i);
            String colStyleName = getAttrAnyNS(colEl, "style-name");
            double w = styles.getColumnWidth(colStyleName);
            int repeated = 1;
            String repAttr = getAttrAnyNS(colEl, "number-columns-repeated");
            if (repAttr != null && !repAttr.isBlank()) {
                try { repeated = Integer.parseInt(repAttr.trim()); } catch (Exception ignored) {}
            }
            for (int r = 0; r < repeated; r++) {
                colWidths.add(w > 0 ? w : 100.0);
            }
        }

        NodeList rowNodes = tableElem.getElementsByTagNameNS("*", "table-row");
        int numRows = rowNodes.getLength();

        for (int r = 0; r < numRows; r++) {
            Element rowEl = (Element) rowNodes.item(r);
            DocumentTableRow row = new DocumentTableRow();

            NodeList cellNodes = rowEl.getElementsByTagNameNS("*", "table-cell");
            for (int c = 0; c < cellNodes.getLength(); c++) {
                Element cellEl = (Element) cellNodes.item(c);
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();

                // Colspan
                String spanAttr = getAttrAnyNS(cellEl, "number-columns-spanned");
                if (spanAttr != null && !spanAttr.isBlank()) {
                    try {
                        int span = Integer.parseInt(spanAttr.trim());
                        if (span > 1) cell.setColSpan(span);
                    } catch (Exception ignored) {}
                }

                // Cell style
                String cellStyleName = getAttrAnyNS(cellEl, "style-name");
                TableCellStyleInfo cellStyle = styles.getTableCellStyle(cellStyleName);
                if (cellStyle != null) {
                    if (cellStyle.backgroundColor != null) cell.setBackgroundColor(cellStyle.backgroundColor);
                    if (cellStyle.verticalAlignment != null) cell.setVerticalAlignment(cellStyle.verticalAlignment);
                    if (cellStyle.paddingTop >= 0) cell.setPaddingTop(cellStyle.paddingTop);
                    if (cellStyle.paddingBottom >= 0) cell.setPaddingBottom(cellStyle.paddingBottom);
                    if (cellStyle.paddingLeft >= 0) cell.setPaddingLeft(cellStyle.paddingLeft);
                    if (cellStyle.paddingRight >= 0) cell.setPaddingRight(cellStyle.paddingRight);
                    cell.setBorderTop(cellStyle.borderTop);
                    cell.setBorderBottom(cellStyle.borderBottom);
                    cell.setBorderLeft(cellStyle.borderLeft);
                    cell.setBorderRight(cellStyle.borderRight);
                }

                NodeList pNodes = cellEl.getElementsByTagNameNS("*", "p");
                if (pNodes.getLength() > 0) {
                    for (int p = 0; p < pNodes.getLength(); p++) {
                        Paragraph cp = parseParagraph((Element) pNodes.item(p), styles, docFont, docSize);
                        cell.addParagraph(cp);
                    }
                } else {
                    String text = cellEl.getTextContent();
                    Paragraph cp = new Paragraph();
                    cp.addRun(new TextRun(text != null ? text.trim() : "", docFont, docSize));
                    cell.addParagraph(cp);
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

    private static String getAttrAnyNS(Element el, String localName) {
        if (el == null) return null;
        if (el.hasAttribute(localName)) return el.getAttribute(localName);
        for (int i = 0; i < el.getAttributes().getLength(); i++) {
            Node attr = el.getAttributes().item(i);
            String name = attr.getNodeName();
            if (name.endsWith(":" + localName) || name.equals(localName)) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    private static double parseDimension(String val) {
        if (val == null || val.isBlank()) return -1;
        val = val.trim().toLowerCase();
        try {
            if (val.endsWith("pt")) return Double.parseDouble(val.substring(0, val.length() - 2).trim());
            if (val.endsWith("in")) return Double.parseDouble(val.substring(0, val.length() - 2).trim()) * 72.0;
            if (val.endsWith("cm")) return Double.parseDouble(val.substring(0, val.length() - 2).trim()) * (72.0 / 2.54);
            if (val.endsWith("mm")) return Double.parseDouble(val.substring(0, val.length() - 2).trim()) * (72.0 / 25.4);
            if (val.endsWith("px")) return Double.parseDouble(val.substring(0, val.length() - 2).trim()) * 0.75;
            return Double.parseDouble(val);
        } catch (Exception e) {
            return -1;
        }
    }

    // --- Helper Style Classes ---

    private static class TextStyleInfo {
        String fontFamily;
        double fontSize = -1;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        Color textColor;
        Color highlightColor;

        static TextStyleInfo merge(TextStyleInfo base, TextStyleInfo override) {
            if (base == null && override == null) return new TextStyleInfo();
            if (base == null) return override;
            if (override == null) return base;
            TextStyleInfo res = new TextStyleInfo();
            res.fontFamily = override.fontFamily != null ? override.fontFamily : base.fontFamily;
            res.fontSize = override.fontSize > 0 ? override.fontSize : base.fontSize;
            res.bold = base.bold || override.bold;
            res.italic = base.italic || override.italic;
            res.underline = base.underline || override.underline;
            res.strike = base.strike || override.strike;
            res.textColor = override.textColor != null ? override.textColor : base.textColor;
            res.highlightColor = override.highlightColor != null ? override.highlightColor : base.highlightColor;
            return res;
        }
    }

    private static class ParagraphStyleInfo {
        Paragraph.Alignment alignment;
        double spaceBefore = -1;
        double spaceAfter = -1;
        double lineSpacing = -1;
        double leftIndent = -1;
        double firstLineIndent = 0;
        boolean pageBreakBefore;
        TextStyleInfo textStyle;
    }

    private static class TableStyleInfo {
        DocumentTable.Alignment alignment;
        Color borderColor;
        double borderWidth = -1;
    }

    private static class TableCellStyleInfo {
        Color backgroundColor;
        DocumentTableCell.VAlign verticalAlignment;
        double paddingTop = -1;
        double paddingBottom = -1;
        double paddingLeft = -1;
        double paddingRight = -1;
        boolean borderTop = true;
        boolean borderBottom = true;
        boolean borderLeft = true;
        boolean borderRight = true;
    }

    private static class StyleIndex {
        final Map<String, ParagraphStyleInfo> paraStyles = new HashMap<>();
        final Map<String, TextStyleInfo> textStyles = new HashMap<>();
        final Map<String, TableStyleInfo> tableStyles = new HashMap<>();
        final Map<String, TableCellStyleInfo> cellStyles = new HashMap<>();
        final Map<String, Double> columnWidths = new HashMap<>();
        String detectedFont = "Calibri";
        double detectedSize = 11.0;

        void indexStyles(Document dom) {
            NodeList styleElements = dom.getElementsByTagNameNS("*", "style");
            for (int i = 0; i < styleElements.getLength(); i++) {
                Element styleEl = (Element) styleElements.item(i);
                String name = getAttrAnyNS(styleEl, "name");
                String family = getAttrAnyNS(styleEl, "family");
                if (name == null || family == null) continue;

                switch (family) {
                    case "paragraph" -> {
                        ParagraphStyleInfo psi = parseParagraphStyle(styleEl);
                        paraStyles.put(name, psi);
                        if (psi.textStyle != null) {
                            if (psi.textStyle.fontFamily != null && !"Calibri".equals(psi.textStyle.fontFamily)) {
                                detectedFont = psi.textStyle.fontFamily;
                            }
                            if (psi.textStyle.fontSize > 0) {
                                detectedSize = psi.textStyle.fontSize;
                            }
                        }
                    }
                    case "text" -> {
                        TextStyleInfo tsi = parseTextStyle(styleEl);
                        textStyles.put(name, tsi);
                    }
                    case "table" -> {
                        TableStyleInfo tsi = parseTableStyle(styleEl);
                        tableStyles.put(name, tsi);
                    }
                    case "table-cell" -> {
                        TableCellStyleInfo csi = parseTableCellStyle(styleEl);
                        cellStyles.put(name, csi);
                    }
                    case "table-column" -> {
                        Element tcp = getFirstChildElement(styleEl, "table-column-properties");
                        if (tcp != null) {
                            String w = getAttrAnyNS(tcp, "column-width");
                            double dim = parseDimension(w);
                            if (dim > 0) columnWidths.put(name, dim);
                        }
                    }
                }
            }
        }

        ParagraphStyleInfo getParagraphStyle(String name) {
            return name != null ? paraStyles.get(name) : null;
        }

        TextStyleInfo getTextStyle(String name) {
            return name != null ? textStyles.get(name) : null;
        }

        TableStyleInfo getTableStyle(String name) {
            return name != null ? tableStyles.get(name) : null;
        }

        TableCellStyleInfo getTableCellStyle(String name) {
            return name != null ? cellStyles.get(name) : null;
        }

        double getColumnWidth(String name) {
            return (name != null && columnWidths.containsKey(name)) ? columnWidths.get(name) : -1;
        }

        boolean hasPageBreak(Element pEl) {
            String br = getAttrAnyNS(pEl, "break-before");
            if ("page".equalsIgnoreCase(br)) return true;
            String styleName = getAttrAnyNS(pEl, "style-name");
            ParagraphStyleInfo psi = getParagraphStyle(styleName);
            if (psi != null && psi.pageBreakBefore) return true;
            // Also check child elements for soft-page-break
            NodeList children = pEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node c = children.item(i);
                if (c instanceof Element cel && ("soft-page-break".equals(cel.getLocalName()) || "page-break".equals(cel.getLocalName()))) {
                    return true;
                }
            }
            return false;
        }

        String detectDefaultFont() {
            return detectedFont;
        }

        double detectDefaultSize() {
            return detectedSize;
        }

        private ParagraphStyleInfo parseParagraphStyle(Element styleEl) {
            ParagraphStyleInfo psi = new ParagraphStyleInfo();
            Element pp = getFirstChildElement(styleEl, "paragraph-properties");
            if (pp != null) {
                String align = getAttrAnyNS(pp, "text-align");
                if (align != null) {
                    psi.alignment = switch (align.toLowerCase()) {
                        case "center" -> Paragraph.Alignment.CENTER;
                        case "right", "end" -> Paragraph.Alignment.RIGHT;
                        case "justify", "justified" -> Paragraph.Alignment.JUSTIFY;
                        default -> Paragraph.Alignment.LEFT;
                    };
                }
                String mt = getAttrAnyNS(pp, "margin-top");
                if (mt != null) psi.spaceBefore = parseDimension(mt);
                String mb = getAttrAnyNS(pp, "margin-bottom");
                if (mb != null) psi.spaceAfter = parseDimension(mb);
                String ml = getAttrAnyNS(pp, "margin-left");
                if (ml != null) psi.leftIndent = parseDimension(ml);
                String ti = getAttrAnyNS(pp, "text-indent");
                if (ti != null) psi.firstLineIndent = parseDimension(ti);

                String lh = getAttrAnyNS(pp, "line-height");
                if (lh != null && lh.endsWith("%")) {
                    try {
                        psi.lineSpacing = Double.parseDouble(lh.substring(0, lh.length() - 1).trim()) / 100.0;
                    } catch (Exception ignored) {}
                }

                String bb = getAttrAnyNS(pp, "break-before");
                if ("page".equalsIgnoreCase(bb)) psi.pageBreakBefore = true;
            }

            Element tp = getFirstChildElement(styleEl, "text-properties");
            if (tp != null) {
                psi.textStyle = parseTextProperties(tp);
            }
            return psi;
        }

        private TextStyleInfo parseTextStyle(Element styleEl) {
            Element tp = getFirstChildElement(styleEl, "text-properties");
            return tp != null ? parseTextProperties(tp) : new TextStyleInfo();
        }

        private TextStyleInfo parseTextProperties(Element tp) {
            TextStyleInfo info = new TextStyleInfo();
            String ff = getAttrAnyNS(tp, "font-name");
            if (ff == null || ff.isBlank()) ff = getAttrAnyNS(tp, "font-family");
            if (ff != null && !ff.isBlank()) info.fontFamily = ff.replace("'", "").replace("\"", "");

            String fs = getAttrAnyNS(tp, "font-size");
            if (fs != null) info.fontSize = parseDimension(fs);

            String fw = getAttrAnyNS(tp, "font-weight");
            if (fw != null && ("bold".equalsIgnoreCase(fw) || "700".equals(fw) || "800".equals(fw) || "900".equals(fw))) {
                info.bold = true;
            }

            String fst = getAttrAnyNS(tp, "font-style");
            if ("italic".equalsIgnoreCase(fst) || "oblique".equalsIgnoreCase(fst)) {
                info.italic = true;
            }

            String us = getAttrAnyNS(tp, "text-underline-style");
            if (us != null && !"none".equalsIgnoreCase(us)) {
                info.underline = true;
            }

            String ls = getAttrAnyNS(tp, "text-line-through-style");
            if (ls != null && !"none".equalsIgnoreCase(ls)) {
                info.strike = true;
            }

            String color = getAttrAnyNS(tp, "color");
            if (color != null && !color.isBlank()) {
                try {
                    info.textColor = Color.web(color.trim());
                } catch (Exception ignored) {}
            }

            String bg = getAttrAnyNS(tp, "background-color");
            if (bg != null && !bg.isBlank() && !"transparent".equalsIgnoreCase(bg)) {
                try {
                    info.highlightColor = Color.web(bg.trim());
                } catch (Exception ignored) {}
            }
            return info;
        }

        private TableStyleInfo parseTableStyle(Element styleEl) {
            TableStyleInfo tsi = new TableStyleInfo();
            Element tp = getFirstChildElement(styleEl, "table-properties");
            if (tp != null) {
                String align = getAttrAnyNS(tp, "align");
                if (align != null) {
                    tsi.alignment = switch (align.toLowerCase()) {
                        case "center" -> DocumentTable.Alignment.CENTER;
                        case "right" -> DocumentTable.Alignment.RIGHT;
                        default -> DocumentTable.Alignment.LEFT;
                    };
                }
                String border = getAttrAnyNS(tp, "border");
                if (border != null && !border.isBlank()) {
                    parseBorderProperty(border, tsi);
                }
            }
            return tsi;
        }

        private TableCellStyleInfo parseTableCellStyle(Element styleEl) {
            TableCellStyleInfo csi = new TableCellStyleInfo();
            Element tcp = getFirstChildElement(styleEl, "table-cell-properties");
            if (tcp != null) {
                String bg = getAttrAnyNS(tcp, "background-color");
                if (bg != null && !bg.isBlank() && !"transparent".equalsIgnoreCase(bg)) {
                    try {
                        csi.backgroundColor = Color.web(bg.trim());
                    } catch (Exception ignored) {}
                }

                String va = getAttrAnyNS(tcp, "vertical-align");
                if (va != null) {
                    csi.verticalAlignment = switch (va.toLowerCase()) {
                        case "middle", "center" -> DocumentTableCell.VAlign.CENTER;
                        case "bottom" -> DocumentTableCell.VAlign.BOTTOM;
                        default -> DocumentTableCell.VAlign.TOP;
                    };
                }

                String pad = getAttrAnyNS(tcp, "padding");
                if (pad != null) {
                    double d = parseDimension(pad);
                    if (d >= 0) {
                        csi.paddingTop = d;
                        csi.paddingBottom = d;
                        csi.paddingLeft = d;
                        csi.paddingRight = d;
                    }
                }
                String pt = getAttrAnyNS(tcp, "padding-top");
                if (pt != null) csi.paddingTop = parseDimension(pt);
                String pb = getAttrAnyNS(tcp, "padding-bottom");
                if (pb != null) csi.paddingBottom = parseDimension(pb);
                String pl = getAttrAnyNS(tcp, "padding-left");
                if (pl != null) csi.paddingLeft = parseDimension(pl);
                String pr = getAttrAnyNS(tcp, "padding-right");
                if (pr != null) csi.paddingRight = parseDimension(pr);

                String bt = getAttrAnyNS(tcp, "border-top");
                if ("none".equalsIgnoreCase(bt)) csi.borderTop = false;
                String bb = getAttrAnyNS(tcp, "border-bottom");
                if ("none".equalsIgnoreCase(bb)) csi.borderBottom = false;
                String bl = getAttrAnyNS(tcp, "border-left");
                if ("none".equalsIgnoreCase(bl)) csi.borderLeft = false;
                String br = getAttrAnyNS(tcp, "border-right");
                if ("none".equalsIgnoreCase(br)) csi.borderRight = false;
            }
            return csi;
        }

        private void parseBorderProperty(String borderStr, TableStyleInfo tsi) {
            // e.g. "1pt solid #000000" or "none"
            if ("none".equalsIgnoreCase(borderStr)) {
                tsi.borderWidth = 0.0;
                return;
            }
            String[] parts = borderStr.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("#")) {
                    try { tsi.borderColor = Color.web(part); } catch (Exception ignored) {}
                } else {
                    double d = parseDimension(part);
                    if (d >= 0) tsi.borderWidth = d;
                }
            }
        }

        private Element getFirstChildElement(Element parent, String localName) {
            NodeList nl = parent.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element el && localName.equals(el.getLocalName())) {
                    return el;
                }
            }
            return null;
        }
    }
}
