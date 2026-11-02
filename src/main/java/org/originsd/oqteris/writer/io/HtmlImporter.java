package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced importer for HTML documents (.html, .htm).
 * Supports:
 * - Page breaks (elements with page-break-before/after: always, <hr class="pagebreak">)
 * - Document default font and size detection
 * - Paragraph formatting (alignment, margins, line-height, text-indent, heading styles, lists)
 * - Text styling (font-family, font-size, bold, italic, underline, strikethrough, color, highlight)
 * - Tables (alignment, borders, cell shading, cell padding, vertical-align, colSpan)
 */
public class HtmlImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "HTML",
            "HTML Document (*.html, *.htm)",
            DocumentType.WRITER,
            List.of("html", "htm"),
            "text/html",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new HtmlImporter());
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
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        String rawContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        // Strip script tags
        String content = rawContent.replaceAll("(?is)<script.*?</script>", "");

        // 1. Detect document default font and size from style or body
        String defaultFont = "Segoe UI";
        double defaultSize = 11.0;

        Pattern fontFacePattern = Pattern.compile("(?i)font-family\\s*:\\s*([^;\"'>]+)");
        Matcher ffm = fontFacePattern.matcher(content);
        if (ffm.find()) {
            String f = ffm.group(1).trim().split(",")[0].trim().replace("\"", "").replace("'", "");
            if (!f.isBlank()) defaultFont = f;
        }

        Pattern fontSizePattern = Pattern.compile("(?i)font-size\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
        Matcher fsm = fontSizePattern.matcher(content);
        if (fsm.find()) {
            try {
                double sz = Double.parseDouble(fsm.group(1));
                String unit = fsm.group(2);
                if ("px".equalsIgnoreCase(unit)) sz *= 0.75;
                if (sz > 0) defaultSize = sz;
            } catch (Exception ignored) {}
        }

        document.setDefaultFontFamily(defaultFont);
        document.setDefaultFontSize(defaultSize);

        // Strip style tag now that default font was checked
        content = content.replaceAll("(?is)<style.*?</style>", "");

        // Extract body if present
        Pattern bodyPattern = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
        Matcher bm = bodyPattern.matcher(content);
        if (bm.find()) {
            content = bm.group(1);
        }

        // 2. Parse top-level block elements sequentially: tables, page breaks, headings, paragraphs, lists
        parseTopLevelBlocks(content, document, defaultFont, defaultSize);

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private void parseTopLevelBlocks(String html, WriterDocument document, String docFont, double docSize) {
        // Tokenize into block elements: table, hr/div pagebreak, h1-h6, p, ul, ol, div, blockquote
        Pattern blockPattern = Pattern.compile("(?is)(<table[^>]*>.*?</table>|<hr[^>]*>|<div[^>]*page-break[^>]*>.*?</div>|<(h[1-6]|p|div|ul|ol|blockquote)([^>]*)>(.*?)</\\2>)");
        Matcher m = blockPattern.matcher(html);

        int lastEnd = 0;
        boolean matchedAny = false;

        while (m.find()) {
            matchedAny = true;
            String matched = m.group(1).trim();

            if (matched.toLowerCase().startsWith("<table")) {
                document.addElement(parseTable(matched, docFont, docSize));
            } else if (isPageBreakElement(matched)) {
                document.addElement(new PageBreakElement());
            } else if (matched.toLowerCase().startsWith("<ul") || matched.toLowerCase().startsWith("<ol")) {
                boolean numbered = matched.toLowerCase().startsWith("<ol");
                parseList(matched, document, docFont, docSize, numbered);
            } else {
                // h1-h6, p, div, blockquote
                String tag = m.group(2) != null ? m.group(2).toLowerCase() : "p";
                String attrs = m.group(3) != null ? m.group(3) : "";
                String inner = m.group(4) != null ? m.group(4) : "";

                if (isPageBreakStyle(attrs)) {
                    document.addElement(new PageBreakElement());
                }

                Paragraph p = new Paragraph();
                applyHeadingStyle(tag, p);
                applyParagraphCss(attrs, p);
                parseInlineRuns(inner, p, docFont, docSize);

                if (!p.getRuns().isEmpty() || !p.getPlainText().isBlank()) {
                    document.addElement(p);
                }
            }
            lastEnd = m.end();
        }

        if (!matchedAny) {
            // Fallback plain lines
            String plain = stripHtml(html);
            String[] lines = plain.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    Paragraph p = new Paragraph();
                    p.addRun(new TextRun(line, docFont, docSize));
                    document.addElement(p);
                }
            }
        }
    }

    private boolean isPageBreakElement(String tagHtml) {
        String lower = tagHtml.toLowerCase();
        return lower.contains("pagebreak") ||
                lower.contains("page-break-before: always") ||
                lower.contains("page-break-before:always") ||
                lower.contains("page-break-after: always") ||
                lower.contains("page-break-after:always") ||
                lower.contains("break-before: page") ||
                lower.contains("break-before:page");
    }

    private boolean isPageBreakStyle(String styleAttr) {
        if (styleAttr == null) return false;
        String lower = styleAttr.toLowerCase();
        return lower.contains("page-break-before: always") ||
                lower.contains("page-break-before:always") ||
                lower.contains("break-before: page") ||
                lower.contains("break-before:page");
    }

    private void applyHeadingStyle(String tag, Paragraph p) {
        switch (tag) {
            case "h1" -> p.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
            case "h2" -> p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            case "h3" -> p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            case "h4", "h5", "h6" -> p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
            default -> p.setHeadingStyle(Paragraph.HeadingStyle.NORMAL);
        }
    }

    private void parseList(String listHtml, WriterDocument doc, String docFont, double docSize, boolean numbered) {
        Pattern liPattern = Pattern.compile("(?is)<li([^>]*)>(.*?)</li>");
        Matcher m = liPattern.matcher(listHtml);
        while (m.find()) {
            String attrs = m.group(1);
            String inner = m.group(2);
            Paragraph p = new Paragraph();
            p.setListType(numbered ? Paragraph.ListType.NUMBERED : Paragraph.ListType.BULLET);
            applyParagraphCss(attrs, p);
            parseInlineRuns(inner, p, docFont, docSize);
            doc.addElement(p);
        }
    }

    private void applyParagraphCss(String attrString, Paragraph p) {
        if (attrString == null || attrString.isBlank()) return;

        // Alignment
        Pattern alignPattern = Pattern.compile("(?i)(?:align\\s*=\\s*[\"']?([a-z]+)[\"']?|text-align\\s*:\\s*([a-z]+))");
        Matcher am = alignPattern.matcher(attrString);
        if (am.find()) {
            String al = am.group(1) != null ? am.group(1) : am.group(2);
            switch (al.toLowerCase()) {
                case "center" -> p.setAlignment(Paragraph.Alignment.CENTER);
                case "right" -> p.setAlignment(Paragraph.Alignment.RIGHT);
                case "justify" -> p.setAlignment(Paragraph.Alignment.JUSTIFY);
                default -> p.setAlignment(Paragraph.Alignment.LEFT);
            }
        }

        // Spacing: margin-top, margin-bottom, line-height, text-indent
        Pattern mt = Pattern.compile("(?i)margin-top\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
        Matcher mtm = mt.matcher(attrString);
        if (mtm.find()) {
            double v = Double.parseDouble(mtm.group(1));
            if ("px".equalsIgnoreCase(mtm.group(2))) v *= 0.75;
            p.setSpaceBefore(v);
        }

        Pattern mb = Pattern.compile("(?i)margin-bottom\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
        Matcher mbm = mb.matcher(attrString);
        if (mbm.find()) {
            double v = Double.parseDouble(mbm.group(1));
            if ("px".equalsIgnoreCase(mbm.group(2))) v *= 0.75;
            p.setSpaceAfter(v);
        }

        Pattern lh = Pattern.compile("(?i)line-height\\s*:\\s*([0-9.]+)");
        Matcher lhm = lh.matcher(attrString);
        if (lhm.find()) {
            p.setLineSpacing(Double.parseDouble(lhm.group(1)));
        }

        Pattern ti = Pattern.compile("(?i)text-indent\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
        Matcher tim = ti.matcher(attrString);
        if (tim.find()) {
            double v = Double.parseDouble(tim.group(1));
            if ("px".equalsIgnoreCase(tim.group(2))) v *= 0.75;
            p.setFirstLineIndent(v);
        }
    }

    private DocumentTable parseTable(String tableHtml, String docFont, double docSize) {
        DocumentTable table = new DocumentTable();
        table.getRows().clear();

        // Extract table attributes / style
        Pattern tblStart = Pattern.compile("(?is)<table([^>]*)>");
        Matcher tsm = tblStart.matcher(tableHtml);
        if (tsm.find()) {
            String tblAttrs = tsm.group(1);
            if (tblAttrs.toLowerCase().contains("align=\"center\"") || tblAttrs.toLowerCase().contains("margin: 0 auto") || tblAttrs.toLowerCase().contains("margin-left: auto; margin-right: auto")) {
                table.setAlignment(DocumentTable.Alignment.CENTER);
            } else if (tblAttrs.toLowerCase().contains("align=\"right\"")) {
                table.setAlignment(DocumentTable.Alignment.RIGHT);
            }

            Pattern borderPattern = Pattern.compile("(?i)border\\s*:\\s*([0-9.]+)(?:pt|px)?\\s+solid\\s+(#[0-9a-fA-F]{3,6}|[a-zA-Z]+)");
            Matcher bm = borderPattern.matcher(tblAttrs);
            if (bm.find()) {
                try {
                    table.setBorderWidth(Double.parseDouble(bm.group(1)));
                    table.setBorderColor(Color.web(bm.group(2)));
                } catch (Exception ignored) {}
            }
        }

        // Parse rows
        Pattern trPattern = Pattern.compile("(?is)<tr([^>]*)>(.*?)</tr>");
        Matcher trm = trPattern.matcher(tableHtml);
        while (trm.find()) {
            DocumentTableRow row = new DocumentTableRow();
            String rowInner = trm.group(2);

            // Parse cells: td and th
            Pattern cellPattern = Pattern.compile("(?is)<(td|th)([^>]*)>(.*?)</\\1>");
            Matcher cm = cellPattern.matcher(rowInner);
            while (cm.find()) {
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();

                String cellAttrs = cm.group(2);
                String cellInner = cm.group(3);

                // Colspan
                Pattern csp = Pattern.compile("(?i)colspan\\s*=\\s*[\"']?([0-9]+)[\"']?");
                Matcher csm = csp.matcher(cellAttrs);
                if (csm.find()) {
                    try {
                        int span = Integer.parseInt(csm.group(1));
                        if (span > 1) cell.setColSpan(span);
                    } catch (Exception ignored) {}
                }

                // Shading
                Pattern bgp = Pattern.compile("(?i)(?:background-color\\s*:\\s*|bgcolor\\s*=\\s*[\"']?)(#[0-9a-fA-F]{3,6}|[a-zA-Z]+)");
                Matcher bgm = bgp.matcher(cellAttrs);
                if (bgm.find()) {
                    try {
                        cell.setBackgroundColor(Color.web(bgm.group(1).trim()));
                    } catch (Exception ignored) {}
                }

                // Vertical alignment
                Pattern vap = Pattern.compile("(?i)(?:vertical-align\\s*:\\s*|valign\\s*=\\s*[\"']?)(middle|center|bottom|top)");
                Matcher vam = vap.matcher(cellAttrs);
                if (vam.find()) {
                    String v = vam.group(1).toLowerCase();
                    if ("middle".equals(v) || "center".equals(v)) cell.setVerticalAlignment(DocumentTableCell.VAlign.CENTER);
                    else if ("bottom".equals(v)) cell.setVerticalAlignment(DocumentTableCell.VAlign.BOTTOM);
                    else cell.setVerticalAlignment(DocumentTableCell.VAlign.TOP);
                }

                // Cell padding
                Pattern padPattern = Pattern.compile("(?i)padding\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
                Matcher pm = padPattern.matcher(cellAttrs);
                if (pm.find()) {
                    double p = Double.parseDouble(pm.group(1));
                    if ("px".equalsIgnoreCase(pm.group(2))) p *= 0.75;
                    cell.setPaddingTop(p);
                    cell.setPaddingBottom(p);
                    cell.setPaddingLeft(p);
                    cell.setPaddingRight(p);
                }

                // Cell borders
                if (cellAttrs.contains("border-top: none")) cell.setBorderTop(false);
                if (cellAttrs.contains("border-bottom: none")) cell.setBorderBottom(false);
                if (cellAttrs.contains("border-left: none")) cell.setBorderLeft(false);
                if (cellAttrs.contains("border-right: none")) cell.setBorderRight(false);

                // Inside cell paragraphs
                Pattern cpPattern = Pattern.compile("(?is)<p([^>]*)>(.*?)</p>");
                Matcher cpm = cpPattern.matcher(cellInner);
                boolean foundInnerP = false;
                while (cpm.find()) {
                    foundInnerP = true;
                    Paragraph cp = new Paragraph();
                    applyParagraphCss(cpm.group(1), cp);
                    parseInlineRuns(cpm.group(2), cp, docFont, docSize);
                    cell.addParagraph(cp);
                }

                if (!foundInnerP) {
                    Paragraph cp = new Paragraph();
                    parseInlineRuns(cellInner, cp, docFont, docSize);
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

    private void parseInlineRuns(String htmlSnippet, Paragraph target, String defaultFont, double defaultSize) {
        String text = htmlSnippet.replaceAll("<br\\s*/?>", "\n");
        text = decodeHtml(text);

        // Parse inline formatting: <b>, <strong>, <i>, <em>, <u>, <s>, <del>, <span>, <font>
        Pattern inlinePattern = Pattern.compile("<(/?[a-zA-Z0-9]+)([^>]*)>");
        Matcher m = inlinePattern.matcher(text);

        int lastIdx = 0;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        Color textColor = Color.BLACK;
        Color highlightColor = null;
        String curFont = defaultFont;
        double curSize = defaultSize;

        while (m.find()) {
            if (m.start() > lastIdx) {
                String sub = text.substring(lastIdx, m.start());
                if (!sub.isEmpty()) {
                    target.addRun(new TextRun(sub, curFont, curSize, bold, italic, underline, strike, textColor, highlightColor));
                }
            }

            String tagName = m.group(1).toLowerCase();
            String attrs = m.group(2);

            switch (tagName) {
                case "b", "strong" -> bold = true;
                case "/b", "/strong" -> bold = false;
                case "i", "em" -> italic = true;
                case "/i", "/em" -> italic = false;
                case "u", "ins" -> underline = true;
                case "/u", "/ins" -> underline = false;
                case "s", "strike", "del" -> strike = true;
                case "/s", "/strike", "/del" -> strike = false;
                case "span", "font" -> {
                    // Extract styles from span / font
                    if (attrs != null) {
                        Pattern colorP = Pattern.compile("(?i)(?:color\\s*:\\s*|color\\s*=\\s*[\"']?)(#[0-9a-fA-F]{3,6}|[a-zA-Z]+)");
                        Matcher cm = colorP.matcher(attrs);
                        if (cm.find()) {
                            try { textColor = Color.web(cm.group(1).trim()); } catch (Exception ignored) {}
                        }

                        Pattern bgP = Pattern.compile("(?i)background-color\\s*:\\s*(#[0-9a-fA-F]{3,6}|[a-zA-Z]+)");
                        Matcher bgm = bgP.matcher(attrs);
                        if (bgm.find()) {
                            try { highlightColor = Color.web(bgm.group(1).trim()); } catch (Exception ignored) {}
                        }

                        Pattern ffP = Pattern.compile("(?i)(?:font-family\\s*:\\s*|face\\s*=\\s*[\"']?)([^;\"'>]+)");
                        Matcher ffm = ffP.matcher(attrs);
                        if (ffm.find()) {
                            String f = ffm.group(1).trim().split(",")[0].trim().replace("\"", "").replace("'", "");
                            if (!f.isBlank()) curFont = f;
                        }

                        Pattern fsP = Pattern.compile("(?i)font-size\\s*:\\s*([0-9.]+)\\s*(pt|px)?");
                        Matcher fsm = fsP.matcher(attrs);
                        if (fsm.find()) {
                            try {
                                double sz = Double.parseDouble(fsm.group(1));
                                if ("px".equalsIgnoreCase(fsm.group(2))) sz *= 0.75;
                                if (sz > 0) curSize = sz;
                            } catch (Exception ignored) {}
                        }
                    }
                }
                case "/span", "/font" -> {
                    textColor = Color.BLACK;
                    highlightColor = null;
                    curFont = defaultFont;
                    curSize = defaultSize;
                }
            }
            lastIdx = m.end();
        }

        if (lastIdx < text.length()) {
            String sub = text.substring(lastIdx);
            if (!sub.isEmpty()) {
                target.addRun(new TextRun(sub, curFont, curSize, bold, italic, underline, strike, textColor, highlightColor));
            }
        }

        if (target.getRuns().isEmpty()) {
            target.addRun(new TextRun("", defaultFont, defaultSize));
        }
    }

    private static String decodeHtml(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static String stripHtml(String html) {
        return decodeHtml(html.replaceAll("<[^>]+>", " "));
    }
}
