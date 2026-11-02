package org.originsd.oqteris.writer.layout;

import javafx.scene.image.Image;
import javafx.scene.text.Font;
import org.originsd.oqteris.core.render.FontManager;
import org.originsd.oqteris.writer.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent text layout, word-wrapping, and multi-page pagination engine.
 * <p>
 * Tab-stop positions are measured from the page left margin (same origin as
 * {@link PageSetup#getMarginLeft()}). Paragraph left/right/first-line indents
 * are applied relative to that margin. Wrapping width is the printable width
 * minus left and right indents.
 */
public class LayoutEngine {
    private static final FontManager FONTS = FontManager.getInstance();
    private static final double DEFAULT_TAB_INTERVAL = 36.0; // 0.5 in

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

                // Content area width accounts for paragraph indents
                double contentW = Math.max(20.0, printableW - p.getLeftIndent() - p.getRightIndent());
                List<LayoutLine> lines = layoutParagraph(p, contentW, p.getLeftIndent());

                for (int i = 0; i < lines.size(); i++) {
                    LayoutLine line = lines.get(i);
                    line.setLastLineOfParagraph(i == lines.size() - 1);

                    if (cursorY + line.getHeight() > setup.getPageHeight() - setup.getMarginBottom()) {
                        currentPage = new LayoutPage(pages.size(), setup);
                        pages.add(currentPage);
                        cursorY = setup.getMarginTop();
                    }

                    // X origin = page left margin + paragraph left indent (+ first-line on first line)
                    double extraFirst = (i == 0) ? p.getFirstLineIndent() : 0.0;
                    line.setX(setup.getMarginLeft() + p.getLeftIndent() + extraFirst);
                    line.setY(cursorY);

                    // Alignment excess is relative to the remaining content width for this line
                    double lineContentW = contentW - (i == 0 ? Math.max(0, p.getFirstLineIndent()) : 0.0);
                    applyAlignment(line, p.getAlignment(), Math.max(10.0, lineContentW));

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

                double targetW = img.getWidth() > 0 ? img.getWidth() : Math.min(naturalW, printableW);
                double targetH = img.getHeight() > 0 ? img.getHeight() : targetW * aspect;

                // Apply rotation if present (90/270 swap dimensions)
                int rot = img.getRotationDegrees() % 360;
                if (rot < 0) rot += 360;
                if (rot == 90 || rot == 270) {
                    double tmp = targetW;
                    targetW = targetH;
                    targetH = tmp;
                }

                Image display = img.getDisplayImage() != null ? img.getDisplayImage() : img.getImage();

                if (img.isFloating()) {
                    // Floating / absolute image — do not advance the text cursor for
                    // IN_FRONT_OF_TEXT / BEHIND_TEXT (text is not displaced).
                    double imgX;
                    double imgY;

                    // Horizontal position
                    if (img.getHAlign() == DocumentImage.HAlign.RIGHT) {
                        if (img.getHRelative() == DocumentImage.HRelative.PAGE) {
                            imgX = setup.getPageWidth() - targetW - Math.max(0, setup.getMarginRight() * 0.15);
                            // Word "align right relative to page" typically pins near right edge
                            imgX = setup.getPageWidth() - targetW;
                        } else {
                            imgX = setup.getMarginLeft() + printableW - targetW;
                        }
                    } else if (img.getHAlign() == DocumentImage.HAlign.CENTER) {
                        if (img.getHRelative() == DocumentImage.HRelative.PAGE) {
                            imgX = (setup.getPageWidth() - targetW) / 2.0;
                        } else {
                            imgX = setup.getMarginLeft() + (printableW - targetW) / 2.0;
                        }
                    } else if (img.getHAlign() == DocumentImage.HAlign.ABSOLUTE) {
                        if (img.getHRelative() == DocumentImage.HRelative.PAGE) {
                            imgX = img.getHOffsetPt();
                        } else if (img.getHRelative() == DocumentImage.HRelative.MARGIN
                                || img.getHRelative() == DocumentImage.HRelative.COLUMN) {
                            imgX = setup.getMarginLeft() + img.getHOffsetPt();
                        } else {
                            imgX = setup.getMarginLeft() + img.getHOffsetPt();
                        }
                    } else { // LEFT
                        if (img.getHRelative() == DocumentImage.HRelative.PAGE) {
                            imgX = img.getHOffsetPt();
                        } else {
                            imgX = setup.getMarginLeft() + img.getHOffsetPt();
                        }
                    }

                    // Vertical position
                    if (img.getVRelative() == DocumentImage.VRelative.PAGE) {
                        imgY = img.getVOffsetPt();
                    } else if (img.getVRelative() == DocumentImage.VRelative.MARGIN
                            || img.getVRelative() == DocumentImage.VRelative.TOP_MARGIN) {
                        imgY = setup.getMarginTop() + img.getVOffsetPt();
                    } else {
                        // PARAGRAPH / LINE — offset relative to current text cursor
                        imgY = cursorY + img.getVOffsetPt();
                    }

                    // Keep image on current page if possible
                    if (imgY + targetH > setup.getPageHeight() - 4
                            && img.getVRelative() != DocumentImage.VRelative.PAGE) {
                        // allow slight overflow for header logos; only force new page for large images
                        if (targetH > printableH * 0.4 && cursorY + targetH > setup.getPageHeight() - setup.getMarginBottom()) {
                            currentPage = new LayoutPage(pages.size(), setup);
                            pages.add(currentPage);
                            cursorY = setup.getMarginTop();
                            imgY = cursorY + img.getVOffsetPt();
                        }
                    }

                    // Clamp image into page bounds (avoid negative Y covering wrong area)
                    if (imgY < 0) imgY = 0;
                    if (imgX < 0) imgX = 0;
                    if (imgX + targetW > setup.getPageWidth()) {
                        imgX = Math.max(0, setup.getPageWidth() - targetW);
                    }

                    LayoutPage.ImageBox box = new LayoutPage.ImageBox(imgX, imgY, targetW, targetH, display);
                    box.rotationDegrees = rot;
                    box.behindText = img.isBehindText()
                            || img.getWrapMode() == DocumentImage.WrapMode.BEHIND_TEXT;
                    box.floating = true;
                    currentPage.addImageBox(box);

                    // Vertical space for text:
                    // - TOP_AND_BOTTOM / SQUARE / TIGHT always reserve space
                    // - Wide banner images (header logos) also reserve space even when
                    //   wrap is IN_FRONT_OF_TEXT, otherwise body text sits under the logo
                    //   and is unreadable (matches typical Word memo layout).
                    boolean reserveSpace =
                            img.getWrapMode() == DocumentImage.WrapMode.TOP_AND_BOTTOM
                                    || img.getWrapMode() == DocumentImage.WrapMode.SQUARE
                                    || img.getWrapMode() == DocumentImage.WrapMode.TIGHT
                                    || (targetW >= setup.getPageWidth() * 0.55
                                    && img.getWrapMode() != DocumentImage.WrapMode.BEHIND_TEXT
                                    && !img.isBehindText());
                    if (reserveSpace) {
                        double bottom = imgY + targetH + Math.max(img.getDistBottom(), 6.0);
                        if (bottom > cursorY) {
                            cursorY = bottom;
                        }
                    }

                } else {
                    // Inline image in document flow
                    if (targetW > printableW) {
                        targetH *= printableW / targetW;
                        targetW = printableW;
                    }
                    double maxH = printableH * 0.85;
                    if (targetH > maxH) {
                        targetH = maxH;
                        targetW = targetH / Math.max(0.01, aspect);
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

                    LayoutPage.ImageBox box = new LayoutPage.ImageBox(imgX, cursorY, targetW, targetH, display);
                    box.rotationDegrees = rot;
                    box.floating = false;
                    currentPage.addImageBox(box);
                    cursorY += targetH + 12.0;
                }

            } else if (el instanceof PageBreakElement) {
                currentPage = new LayoutPage(pages.size(), setup);
                pages.add(currentPage);
                cursorY = setup.getMarginTop();
            }
        }

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
            default -> 0.0;
        };
    }

    /**
     * @param contentWidth   width available for text after left/right indents
     * @param leftIndent     paragraph left indent (so tab positions can be converted
     *                       from page-margin origin to content origin)
     */
    private static List<LayoutLine> layoutParagraph(Paragraph p, double contentWidth, double leftIndent) {
        List<LayoutLine> lines = new ArrayList<>();
        List<TextRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            Font font = FONTS.getFont("Calibri", 11.0, false, false);
            double h = Math.max(FONTS.measureHeight(font), 11.0) * p.getLineSpacing();
            lines.add(new LayoutLine(p, 0, 0, h, h * 0.78));
            return lines;
        }

        double headingFontSize = getHeadingFontSize(p.getHeadingStyle());

        String prefix = "";
        if (p.getListType() == Paragraph.ListType.BULLET) {
            prefix = "• ";
        } else if (p.getListType() == Paragraph.ListType.NUMBERED) {
            prefix = p.getListIndex() + ". ";
        }

        List<Token> tokens = new ArrayList<>();

        for (int rIdx = 0; rIdx < runs.size(); rIdx++) {
            TextRun run = runs.get(rIdx);
            Font font = FONTS.getFont(run.getFontFamily(), run.getFontSize(), run.isBold(), run.isItalic());
            double measuredH = FONTS.measureHeight(font);
            double runLineH = Math.max(measuredH, run.getFontSize()) * p.getLineSpacing();
            if (headingFontSize > 0 && (run.getFontSize() <= 12.0 || !run.isBold())) {
                font = FONTS.getFont(run.getFontFamily(), headingFontSize,
                        true, run.isItalic());
                double measuredH2 = FONTS.measureHeight(font);
                runLineH = Math.max(measuredH2, headingFontSize) * p.getLineSpacing();
            }

            String text = run.getText();
            if (rIdx == 0 && !prefix.isEmpty()) {
                text = prefix + text;
            }

            // Preserve tabs and newlines as distinct tokens; split on whitespace boundaries
            String[] words = text.split("(?<=\\s)|(?=\\s)");
            for (String word : words) {
                boolean isTab = "\t".equals(word);
                double w = isTab ? 0.0 : FONTS.measureWidth(word, font);
                tokens.add(new Token(word, run, font, w, runLineH, isTab));
            }
        }

        LayoutLine currentLine = null;
        double currentLineWidth = 0;
        boolean isFirstLine = true;
        double firstLineExtra = Math.max(0, p.getFirstLineIndent());

        for (int tIdx = 0; tIdx < tokens.size(); tIdx++) {
            Token t = tokens.get(tIdx);

            // Handle forced newline
            if ("\n".equals(t.word) || "\r\n".equals(t.word) || "\r".equals(t.word)) {
                if (currentLine == null) {
                    currentLine = new LayoutLine(p, 0, 0, t.runLineH, t.runLineH * 0.78);
                    lines.add(currentLine);
                }
                currentLine = null;
                currentLineWidth = 0;
                isFirstLine = false;
                continue;
            }

            // Effective available width for this line (first line may be narrower by first-line indent)
            double lineAvail = contentWidth - (isFirstLine ? firstLineExtra : 0.0);
            if (lineAvail < 20) lineAvail = 20;

            double wordW;
            if (t.isTab) {
                wordW = resolveTabAdvance(p, currentLineWidth, leftIndent, lineAvail, tIdx, tokens);
            } else {
                wordW = t.measuredWidth;
            }

            if (currentLine == null) {
                currentLine = new LayoutLine(p, 0, 0, t.runLineH, t.runLineH * 0.78);
                lines.add(currentLine);
            }

            if (currentLineWidth + wordW > lineAvail + 0.5 && !currentLine.getSegments().isEmpty()) {
                // Wrap to next line
                currentLine = new LayoutLine(p, 0, 0, t.runLineH, t.runLineH * 0.78);
                lines.add(currentLine);
                currentLineWidth = 0;
                isFirstLine = false;
                if (t.word.isBlank() && !t.isTab) continue;
                // Recompute tab for new line if needed
                if (t.isTab) {
                    wordW = resolveTabAdvance(p, 0, leftIndent, contentWidth, tIdx, tokens);
                }
            }

            if (t.runLineH > currentLine.getHeight()) {
                currentLine.setHeight(t.runLineH);
                currentLine.setBaseline(t.runLineH * 0.78);
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

    /**
     * Resolve the advance width produced by a tab character.
     * TabStop.position is always from the page left margin.
     * currentLineWidth is measured from the start of the content area
     * (i.e. after leftIndent + any first-line indent already applied to the line).
     */
    private static double resolveTabAdvance(Paragraph p, double currentLineWidth, double leftIndent,
                                            double lineAvail, int tIdx, List<Token> tokens) {
        TabStop matched = null;
        for (TabStop ts : p.getTabStops()) {
            double relative = ts.getPosition() - leftIndent;
            if (relative > currentLineWidth + 0.5) {
                matched = ts;
                break;
            }
        }

        if (matched != null) {
            double relativeTarget = matched.getPosition() - leftIndent;
            if (relativeTarget > lineAvail) relativeTarget = lineAvail;

            double followingW = 0;
            double decimalOffset = 0;
            for (int n = tIdx + 1; n < tokens.size(); n++) {
                Token next = tokens.get(n);
                if (next.isTab) break;
                followingW += next.measuredWidth;
                if (matched.getAlignment() == TabStop.Alignment.DECIMAL && decimalOffset == 0) {
                    int dot = next.word.indexOf('.');
                    if (dot >= 0) {
                        decimalOffset = FONTS.measureWidth(next.word.substring(0, dot), next.font);
                    }
                }
            }

            return switch (matched.getAlignment()) {
                case RIGHT -> Math.max(4.0, relativeTarget - followingW - currentLineWidth);
                case CENTER -> Math.max(4.0, relativeTarget - followingW / 2.0 - currentLineWidth);
                case DECIMAL -> Math.max(4.0, relativeTarget - decimalOffset - currentLineWidth);
                default -> Math.max(4.0, relativeTarget - currentLineWidth);
            };
        }

        double nextTab = (Math.floor(currentLineWidth / DEFAULT_TAB_INTERVAL) + 1.0) * DEFAULT_TAB_INTERVAL;
        if (nextTab > lineAvail) nextTab = lineAvail;
        return Math.max(4.0, nextTab - currentLineWidth);
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
            line.setX(line.getX() + excess / 2.0);
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

    private static double layoutTable(DocumentTable table, PageSetup setup, List<LayoutPage> pages,
                                      LayoutPage activePage, double cursorY, double printableW) {
        int colCount = Math.max(1, table.getColumnCount());
        double[] widths = table.getColumnWidths();
        if (widths == null || widths.length == 0) {
            widths = new double[colCount];
            double w = printableW / colCount;
            for (int i = 0; i < colCount; i++) widths[i] = w;
        } else if (widths.length != colCount) {
            colCount = widths.length;
        }

        // Scale column widths to fit printable area (prevent table overflowing the page)
        double sum = 0;
        for (double w : widths) sum += w;
        if (sum <= 0) {
            widths = new double[colCount];
            for (int i = 0; i < colCount; i++) widths[i] = printableW / colCount;
            sum = printableW;
        } else if (sum > printableW + 0.5) {
            double scale = printableW / sum;
            for (int i = 0; i < widths.length; i++) widths[i] *= scale;
            sum = printableW;
        }
        final double tableWidth = Math.min(sum, printableW);

        double tableX = setup.getMarginLeft();
        if (table.getAlignment() == DocumentTable.Alignment.CENTER) {
            tableX = setup.getMarginLeft() + Math.max(0, (printableW - tableWidth) / 2.0);
        } else if (table.getAlignment() == DocumentTable.Alignment.RIGHT) {
            tableX = setup.getMarginLeft() + Math.max(0, printableW - tableWidth);
        }

        LayoutPage currentPage = activePage;
        double currentY = cursorY;

        for (DocumentTableRow row : table.getRows()) {
            double rowH = row.getHeight() > 0 ? row.getHeight() : 20.0;
            List<List<LayoutLine>> cellLinesList = new ArrayList<>();
            List<Double> cellWidthsList = new ArrayList<>();

            int colIdx = 0;
            for (int c = 0; c < row.getCells().size(); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                int span = Math.max(1, cell.getColSpan());
                double cellW = 0;
                for (int s = 0; s < span && (colIdx + s) < widths.length; s++) {
                    cellW += widths[colIdx + s];
                }
                if (cellW <= 0) {
                    cellW = (colIdx < widths.length) ? widths[colIdx] : 80.0;
                }
                colIdx += span;
                cellWidthsList.add(cellW);

                double availCellW = Math.max(10, cellW - cell.getPaddingLeft() - cell.getPaddingRight());
                List<LayoutLine> allCellLines = new ArrayList<>();
                double cellContentH = 0;
                for (Paragraph p : cell.getParagraphs()) {
                    List<LayoutLine> lines = layoutParagraph(p, availCellW, 0);
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

            LayoutPage.TableBox tBox = new LayoutPage.TableBox(
                    tableX, currentY, tableWidth, rowH,
                    table.getBorderColor(), table.getBorderWidth());

            double cellX = tableX;
            for (int c = 0; c < row.getCells().size(); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                double cellW = (c < cellWidthsList.size()) ? cellWidthsList.get(c) : 80.0;
                double availCellW = Math.max(10, cellW - cell.getPaddingLeft() - cell.getPaddingRight());

                LayoutPage.TableCellBox cBox = new LayoutPage.TableCellBox(cellX, currentY, cellW, rowH, cell);

                List<LayoutLine> cellLines = (c < cellLinesList.size()) ? cellLinesList.get(c) : List.of();
                double cellContentH = 0;
                for (LayoutLine l : cellLines) cellContentH += l.getHeight();
                double totalH = cellContentH + cell.getPaddingTop() + cell.getPaddingBottom();

                double vOffset = 0;
                if (cell.getVerticalAlignment() == DocumentTableCell.VAlign.CENTER) {
                    vOffset = Math.max(0, (rowH - totalH) / 2.0);
                } else if (cell.getVerticalAlignment() == DocumentTableCell.VAlign.BOTTOM) {
                    vOffset = Math.max(0, rowH - totalH);
                }

                double lineY = currentY + cell.getPaddingTop() + vOffset;
                for (LayoutLine l : cellLines) {
                    l.setX(cellX + cell.getPaddingLeft());
                    l.setY(lineY);
                    applyAlignment(l, l.getParagraph() != null ? l.getParagraph().getAlignment() : Paragraph.Alignment.LEFT, availCellW);
                    cBox.lines.add(l);
                    lineY += l.getHeight();
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
            List<LayoutLine> lines = layoutParagraph(hp, setup.getPrintableWidth(), 0);
            if (!lines.isEmpty()) {
                LayoutLine hl = lines.get(0);
                hl.setX(setup.getMarginLeft());
                hl.setY(Math.max(4, setup.getMarginTop() / 2.0 - hl.getHeight() / 2));
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
            List<LayoutLine> lines = layoutParagraph(fp, setup.getPrintableWidth(), 0);
            if (!lines.isEmpty()) {
                LayoutLine fl = lines.get(0);
                fl.setX(setup.getMarginLeft());
                fl.setY(setup.getPageHeight() - Math.max(fl.getHeight() + 4, setup.getMarginBottom() / 1.5));
                applyAlignment(fl, fp.getAlignment(), setup.getPrintableWidth());
                page.setFooterLine(fl);
            }
        }
    }

    /** Simple token used during paragraph layout. */
    private static final class Token {
        final String word;
        final TextRun run;
        final Font font;
        final double measuredWidth;
        final double runLineH;
        final boolean isTab;

        Token(String word, TextRun run, Font font, double measuredWidth, double runLineH, boolean isTab) {
            this.word = word;
            this.run = run;
            this.font = font;
            this.measuredWidth = measuredWidth;
            this.runLineH = runLineH;
            this.isTab = isTab;
        }
    }
}
