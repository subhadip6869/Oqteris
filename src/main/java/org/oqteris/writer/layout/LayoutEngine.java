package org.oqteris.writer.layout;

import javafx.scene.text.Font;
import org.oqteris.core.render.FontManager;
import org.oqteris.writer.model.*;

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
                double imgW = Math.min(img.getWidth(), printableW);
                double imgH = img.getHeight() * (imgW / img.getWidth());

                if (cursorY + imgH > setup.getPageHeight() - setup.getMarginBottom()) {
                    currentPage = new LayoutPage(pages.size(), setup);
                    pages.add(currentPage);
                    cursorY = setup.getMarginTop();
                }

                double imgX = setup.getMarginLeft();
                if ("CENTER".equalsIgnoreCase(img.getAlignment())) {
                    imgX += (printableW - imgW) / 2.0;
                } else if ("RIGHT".equalsIgnoreCase(img.getAlignment())) {
                    imgX += (printableW - imgW);
                }

                currentPage.addImageBox(new LayoutPage.ImageBox(imgX, cursorY, imgW, imgH, img.getImage()));
                cursorY += imgH + 8.0;

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

    private static List<LayoutLine> layoutParagraph(Paragraph p, double availableWidth) {
        List<LayoutLine> lines = new ArrayList<>();
        List<TextRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            // Empty paragraph has standard blank line height
            Font font = FONTS.getFont("Segoe UI", 11.0, false, false);
            double h = 11.0 * p.getLineSpacing() * 1.25;
            lines.add(new LayoutLine(p, 0, 0, h, h * 0.8));
            return lines;
        }

        // List prefix
        String prefix = "";
        if (p.getListType() == Paragraph.ListType.BULLET) {
            prefix = "• ";
        } else if (p.getListType() == Paragraph.ListType.NUMBERED) {
            prefix = p.getListIndex() + ". ";
        }

        LayoutLine currentLine = null;
        double currentLineWidth = 0;
        double maxLineHeight = 14.0;

        for (int rIdx = 0; rIdx < runs.size(); rIdx++) {
            TextRun run = runs.get(rIdx);
            Font font = FONTS.getFont(run.getFontFamily(), run.getFontSize(), run.isBold(), run.isItalic());
            double runLineH = run.getFontSize() * p.getLineSpacing() * 1.25;
            maxLineHeight = Math.max(maxLineHeight, runLineH);

            String text = run.getText();
            if (rIdx == 0 && !prefix.isEmpty()) {
                text = prefix + text;
            }

            String[] words = text.split("(?<=\\s)|(?=\\s)");

            for (String word : words) {
                double wordW = FONTS.measureWidth(word, font);

                if (currentLine == null) {
                    currentLine = new LayoutLine(p, 0, 0, maxLineHeight, maxLineHeight * 0.8);
                    lines.add(currentLine);
                }

                if (currentLineWidth + wordW > availableWidth && !currentLine.getSegments().isEmpty()) {
                    // Wrap to next line
                    currentLine = new LayoutLine(p, 0, 0, maxLineHeight, maxLineHeight * 0.8);
                    lines.add(currentLine);
                    currentLineWidth = 0;
                    if (word.isBlank()) continue; // skip leading space on wrapped line
                }

                LayoutLine.LineSegment segment = new LayoutLine.LineSegment(word, run, font, wordW, currentLineWidth);
                currentLine.addSegment(segment);
                currentLineWidth += wordW;
            }
        }

        if (lines.isEmpty()) {
            double h = 11.0 * p.getLineSpacing() * 1.25;
            lines.add(new LayoutLine(p, 0, 0, h, h * 0.8));
        }

        return lines;
    }

    private static void applyAlignment(LayoutLine line, Paragraph.Alignment alignment, double availableWidth) {
        double excess = availableWidth - line.getWidth();
        if (excess <= 0) return;

        if (alignment == Paragraph.Alignment.CENTER) {
            double shift = excess / 2.0;
            line.setX(line.getX() + shift);
        } else if (alignment == Paragraph.Alignment.RIGHT) {
            line.setX(line.getX() + excess);
        } else if (alignment == Paragraph.Alignment.JUSTIFY && !line.isLastLineOfParagraph()) {
            // Distribute spaces
            int spaceCount = 0;
            for (LayoutLine.LineSegment s : line.getSegments()) {
                if (s.getText().endsWith(" ") || s.getText().equals(" ")) spaceCount++;
            }
            if (spaceCount > 0) {
                double extraPerSpace = excess / spaceCount;
                double curX = 0;
                for (LayoutLine.LineSegment s : line.getSegments()) {
                    s.setX(curX);
                    curX += s.getWidth();
                    if (s.getText().endsWith(" ") || s.getText().equals(" ")) {
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
