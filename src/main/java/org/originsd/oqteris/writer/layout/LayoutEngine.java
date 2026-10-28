package org.originsd.oqteris.writer.layout;

import javafx.scene.text.Font;
import org.originsd.oqteris.core.render.FontManager;
import org.originsd.oqteris.writer.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent text layout, word-wrapping, and multi-page pagination engine.
 */
public class LayoutEngine {
    private static final FontManager FONTS = FontManager.getInstance();

    public static List<LayoutPage> layout(WriterDocument document) {
        List<LayoutPage> pages = new ArrayList<>();
        if (document == null) return pages;

        PageSetup setup = document.getPageSetup();
        double printableW = setup.getPrintableWidth();
        double printableH = setup.getPrintableHeight();

        LayoutPage currentPage = new LayoutPage(0, setup);
        pages.add(currentPage);

        double cursorY = setup.getMarginTop();

        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) {
                cursorY += p.getSpaceBefore();
                List<LayoutLine> lines = layoutParagraph(p, printableW);

                for (int i = 0; i < lines.size(); i++) {
                    LayoutLine line = lines.get(i);
                    line.setLastLineOfParagraph(i == lines.size() - 1);

                    if (cursorY + line.getHeight() > setup.getPageHeight() - setup.getMarginBottom()) {
                        // Start new page
                        currentPage = new LayoutPage(pages.size(), setup);
                        pages.add(currentPage);
                        cursorY = setup.getMarginTop();
                    }

                    line.setX(setup.getMarginLeft() + p.getLeftIndent());
                    line.setY(cursorY);
                    applyAlignment(line, p.getAlignment(), printableW - p.getLeftIndent() - p.getRightIndent());

                    currentPage.addLine(line);
                    cursorY += line.getHeight();
                }

                cursorY += p.getSpaceAfter();

            } else if (el instanceof DocumentTable table) {
                cursorY = layoutTable(table, setup, pages, currentPage, cursorY, printableW);
                currentPage = pages.get(pages.size() - 1);

            } else if (el instanceof DocumentImage img) {
                double naturalW = (img.getImage() != null && img.getImage().getWidth() > 0)
                        ? img.getImage().getWidth() : img.getWidth();
                double naturalH = (img.getImage() != null && img.getImage().getHeight() > 0)
                        ? img.getImage().getHeight() : img.getHeight();
                double aspect = naturalH / Math.max(1.0, naturalW);

                // Preserve exact aspect ratio and constrain to page width
                double targetW = img.getWidth() > 0 ? Math.min(img.getWidth(), printableW) : Math.min(naturalW, printableW);
                double targetH = targetW * aspect;

                // Clamp to printable height (max 80% of page height)
                double maxH = printableH * 0.8;
                if (targetH > maxH) {
                    targetH = maxH;
                    targetW = targetH / aspect;
                }

                if (cursorY + targetH > setup.getPageHeight() - setup.getMarginBottom()) {
                    currentPage = new LayoutPage(pages.size(), setup);
                    pages.add(currentPage);
                    cursorY = setup.getMarginTop();
                }

                double imgX = setup.getMarginLeft();
                if ("CENTER".equalsIgnoreCase(img.getAlignment())) {
                    imgX += (printableW - targetW) / 2.0;
                } else if ("RIGHT".equalsIgnoreCase(img.getAlignment())) {
                    imgX += (printableW - targetW);
                }

                currentPage.addImageBox(new LayoutPage.ImageBox(imgX, cursorY, targetW, targetH, img.getImage()));
                cursorY += targetH + 12.0;

            } else if (el instanceof PageBreakElement) {
                currentPage = new LayoutPage(pages.size(), setup);
                pages.add(currentPage);
                cursorY = setup.getMarginTop();
            }
        }

        // Resolve Headers and Footers with page numbering
        int totalPages = pages.size();
        for (LayoutPage page : pages) {
            resolveHeaderFooter(document, page, totalPages);
        }

        return pages;
    }

    private static double getHeadingFontSize(Paragraph.HeadingStyle style) {
        return switch (style) {
            case TITLE -> 26.0;
            case HEADING_1 -> 20.0;
            case HEADING_2 -> 16.0;
            case HEADING_3 -> 14.0;
            default -> 0.0; // 0 means no override
        };
    }

    private static List<LayoutLine> layoutParagraph(Paragraph p, double availableWidth) {
        List<LayoutLine> lines = new ArrayList<>();
        List<TextRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            // Empty paragraph has standard blank line height
            Font font = FONTS.getFont("Segoe UI", 11.0, false, false);
            double h = Math.max(FONTS.measureHeight(font), 11.0) * p.getLineSpacing();
            lines.add(new LayoutLine(p, 0, 0, h, h * 0.78));
            return lines;
        }

        // Apply heading default sizes for runs if heading style is set
        double headingFontSize = getHeadingFontSize(p.getHeadingStyle());

        // List prefix
        String prefix = "";
        if (p.getListType() == Paragraph.ListType.BULLET) {
            prefix = "• ";
        } else if (p.getListType() == Paragraph.ListType.NUMBERED) {
            prefix = p.getListIndex() + ". ";
        }

        // 1. Tokenize paragraph into flat list of tokens
        record Token(String word, TextRun run, Font font, double measuredWidth, double runLineH, boolean isTab) {}
        List<Token> tokens = new ArrayList<>();

        for (int rIdx = 0; rIdx < runs.size(); rIdx++) {
            TextRun run = runs.get(rIdx);
            Font font = FONTS.getFont(run.getFontFamily(), run.getFontSize(), run.isBold(), run.isItalic());
            double measuredH = FONTS.measureHeight(font);
            double runLineH = Math.max(measuredH, run.getFontSize()) * p.getLineSpacing();
            if (headingFontSize > 0 && run.getFontSize() <= 11.5) {
                font = FONTS.getFont(run.getFontFamily(), headingFontSize, run.isBold() || p.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL, run.isItalic());
                double measuredH2 = FONTS.measureHeight(font);
                runLineH = Math.max(measuredH2, headingFontSize) * p.getLineSpacing();
            }

            String text = run.getText();
            if (rIdx == 0 && !prefix.isEmpty()) {
                text = prefix + text;
            }

            String[] words = text.split("(?<=\\s)|(?=\\s)");
            for (String word : words) {
                boolean isTab = "\t".equals(word);
                double w = isTab ? 0.0 : FONTS.measureWidth(word, font);
                tokens.add(new Token(word, run, font, w, runLineH, isTab));
            }
        }

        LayoutLine currentLine = null;
        double currentLineWidth = 0;
        double maxLineHeight = 14.0;

        for (int tIdx = 0; tIdx < tokens.size(); tIdx++) {
            Token t = tokens.get(tIdx);
            maxLineHeight = Math.max(maxLineHeight, t.runLineH);

            double wordW;
            if (t.isTab) {
                // Find next custom tab stop
                TabStop matchedStop = null;
                for (TabStop ts : p.getTabStops()) {
                    if (ts.getPosition() > currentLineWidth + 1.0) {
                        matchedStop = ts;
                        break;
                    }
                }

                if (matchedStop != null) {
                    double followingW = 0;
                    for (int n = tIdx + 1; n < tokens.size(); n++) {
                        Token nextTok = tokens.get(n);
                        if (nextTok.isTab) break;
                        followingW += nextTok.measuredWidth;
                    }

                    double targetPos = matchedStop.getPosition();
                    if (targetPos > availableWidth) targetPos = availableWidth;

                    if (matchedStop.getAlignment() == TabStop.Alignment.RIGHT) {
                        double targetStart = targetPos - followingW;
                        wordW = Math.max(12.0, targetStart - currentLineWidth);
                    } else if (matchedStop.getAlignment() == TabStop.Alignment.CENTER) {
                        double targetStart = targetPos - followingW / 2.0;
                        wordW = Math.max(12.0, targetStart - currentLineWidth);
                    } else {
                        wordW = Math.max(12.0, targetPos - currentLineWidth);
                    }
                } else {
                    double tabInterval = 36.0; // 0.5 inch tab stop standard in Office
                    double nextTab = (Math.floor(currentLineWidth / tabInterval) + 1.0) * tabInterval;
                    wordW = Math.max(12.0, nextTab - currentLineWidth);
                }
            } else {
                wordW = t.measuredWidth;
            }

            if (currentLine == null) {
                currentLine = new LayoutLine(p, 0, 0, maxLineHeight, maxLineHeight * 0.78);
                lines.add(currentLine);
            }

            if (currentLineWidth + wordW > availableWidth && !currentLine.getSegments().isEmpty()) {
                // Wrap to next line
                currentLine = new LayoutLine(p, 0, 0, maxLineHeight, maxLineHeight * 0.78);
                lines.add(currentLine);
                currentLineWidth = 0;
                if (t.word.isBlank() && !t.isTab) continue; // skip leading space on wrapped line
            }

            LayoutLine.LineSegment segment = new LayoutLine.LineSegment(t.word, t.run, t.font, wordW, currentLineWidth);
            currentLine.addSegment(segment);
            currentLineWidth += wordW;
        }

        if (lines.isEmpty()) {
            Font font = FONTS.getFont("Calibri", 11.0, false, false);
            double h = Math.max(FONTS.measureHeight(font), 11.0) * p.getLineSpacing();
            lines.add(new LayoutLine(p, 0, 0, h, h * 0.78));
        }

        return lines;
    }

    private static void applyAlignment(LayoutLine line, Paragraph.Alignment alignment, double availableWidth) {
        double trailingSpaceW = 0;
        List<LayoutLine.LineSegment> segs = line.getSegments();
        for (int i = segs.size() - 1; i >= 0; i--) {
            LayoutLine.LineSegment s = segs.get(i);
            if (s.getText().isBlank()) {
                trailingSpaceW += s.getWidth();
            } else {
                break;
            }
        }
        double contentWidth = Math.max(0, line.getWidth() - trailingSpaceW);
        double excess = availableWidth - contentWidth;
        if (excess <= 0) return;

        if (alignment == Paragraph.Alignment.CENTER) {
            double shift = excess / 2.0;
            line.setX(line.getX() + shift);
        } else if (alignment == Paragraph.Alignment.RIGHT) {
            line.setX(line.getX() + excess);
        } else if (alignment == Paragraph.Alignment.JUSTIFY && !line.isLastLineOfParagraph()) {
            int spaceCount = 0;
            for (int i = 0; i < segs.size() - 1; i++) {
                LayoutLine.LineSegment s = segs.get(i);
                if (s.getText().endsWith(" ") || s.getText().equals(" ")) spaceCount++;
            }
            if (spaceCount > 0) {
                double extraPerSpace = excess / spaceCount;
                double curX = 0;
                for (int i = 0; i < segs.size(); i++) {
                    LayoutLine.LineSegment s = segs.get(i);
                    s.setX(curX);
                    curX += s.getWidth();
                    if (i < segs.size() - 1 && (s.getText().endsWith(" ") || s.getText().equals(" "))) {
                        curX += extraPerSpace;
                    }
                }
            }
        }
    }

    private static double layoutTable(DocumentTable table, PageSetup setup, List<LayoutPage> pages, LayoutPage activePage, double cursorY, double printableW) {
        int colCount = Math.max(1, table.getColumnCount());
        double[] widths = table.getColumnWidths();
        if (widths == null || widths.length != colCount) {
            widths = new double[colCount];
            double w = printableW / colCount;
            for (int i = 0; i < colCount; i++) widths[i] = w;
        }

        LayoutPage currentPage = activePage;
        double currentY = cursorY;

        for (DocumentTableRow row : table.getRows()) {
            // Measure row height
            double rowH = row.getHeight() > 0 ? row.getHeight() : 24.0;
            List<List<LayoutLine>> cellLinesList = new ArrayList<>();

            for (int c = 0; c < row.getCells().size(); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                double cellW = (c < widths.length) ? widths[c] : 80.0;
                double availCellW = cellW - cell.getPaddingLeft() - cell.getPaddingRight();

                List<LayoutLine> allCellLines = new ArrayList<>();
                double cellContentH = 0;
                for (Paragraph p : cell.getParagraphs()) {
                    List<LayoutLine> lines = layoutParagraph(p, availCellW);
                    allCellLines.addAll(lines);
                    for (LayoutLine l : lines) cellContentH += l.getHeight();
                }

                double totalH = cellContentH + cell.getPaddingTop() + cell.getPaddingBottom();
                rowH = Math.max(rowH, totalH);
                cellLinesList.add(allCellLines);
            }

            if (currentY + rowH > setup.getPageHeight() - setup.getMarginBottom()) {
                currentPage = new LayoutPage(pages.size(), setup);
                pages.add(currentPage);
                currentY = setup.getMarginTop();
            }

            LayoutPage.TableBox tBox = new LayoutPage.TableBox(setup.getMarginLeft(), currentY, printableW, rowH, table.getBorderColor(), table.getBorderWidth());

            double cellX = setup.getMarginLeft();
            for (int c = 0; c < row.getCells().size(); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                double cellW = (c < widths.length) ? widths[c] : 80.0;

                LayoutPage.TableCellBox cBox = new LayoutPage.TableCellBox(cellX, currentY, cellW, rowH, cell);

                double lineY = currentY + cell.getPaddingTop();
                if (c < cellLinesList.size()) {
                    for (LayoutLine l : cellLinesList.get(c)) {
                        l.setX(cellX + cell.getPaddingLeft());
                        l.setY(lineY);
                        cBox.lines.add(l);
                        lineY += l.getHeight();
                    }
                }

                tBox.cells.add(cBox);
                cellX += cellW;
            }

            currentPage.addTableBox(tBox);
            currentY += rowH;
        }

        return currentY + 8.0;
    }

    private static void resolveHeaderFooter(WriterDocument doc, LayoutPage page, int totalPages) {
        PageSetup setup = page.getPageSetup();
        int pageNum = page.getPageIndex() + 1;

        if (doc.getHeader() != null) {
            String text = doc.getHeader().getPlainText()
                    .replace("$PAGE", String.valueOf(pageNum))
                    .replace("$PAGES", String.valueOf(totalPages));
            Paragraph hp = new Paragraph(text);
            hp.setAlignment(doc.getHeader().getAlignment());
            List<LayoutLine> lines = layoutParagraph(hp, setup.getPrintableWidth());
            if (!lines.isEmpty()) {
                LayoutLine hl = lines.get(0);
                hl.setX(setup.getMarginLeft());
                hl.setY(setup.getMarginTop() / 2.0);
                applyAlignment(hl, hp.getAlignment(), setup.getPrintableWidth());
                page.setHeaderLine(hl);
            }
        }

        if (doc.getFooter() != null) {
            String text = doc.getFooter().getPlainText()
                    .replace("$PAGE", String.valueOf(pageNum))
                    .replace("$PAGES", String.valueOf(totalPages));
            Paragraph fp = new Paragraph(text);
            fp.setAlignment(doc.getFooter().getAlignment());
            List<LayoutLine> lines = layoutParagraph(fp, setup.getPrintableWidth());
            if (!lines.isEmpty()) {
                LayoutLine fl = lines.get(0);
                fl.setX(setup.getMarginLeft());
                fl.setY(setup.getPageHeight() - (setup.getMarginBottom() / 1.5));
                applyAlignment(fl, fp.getAlignment(), setup.getPrintableWidth());
                page.setFooterLine(fl);
            }
        }
    }
}
