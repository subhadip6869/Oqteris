package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Enhanced importer for Rich Text Format (.rtf) documents.
 * Supports:
 * - Font table (\fonttbl) and Color table (\colortbl)
 * - Default document font and size detection
 * - Page breaks (\page) -> PageBreakElement
 * - Paragraph formatting: alignment (\qc, \qr, \qj, \ql), margins/spacing (\sb, \sa, \sl), indents (\li, \fi)
 * - Text styling: font, size (\fs), bold (\b), italic (\i), underline, strikethrough (\strike),
 *   text color (\cf), highlight color (\highlight / \cb)
 * - Tables (\trowd, \cell, \row) with row alignment, cell widths (\cellx), cell shading (\clcbpat),
 *   cell padding (\clpadt, etc.), and vertical alignment (\clvertalc, etc.).
 */
public class RtfImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "RTF",
            "Rich Text Format (*.rtf)",
            DocumentType.WRITER,
            List.of("rtf"),
            "application/rtf",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new RtfImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".rtf");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            bytes = fis.readAllBytes();
        }

        new Parser(bytes, document).parse();

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private static class Parser {
        private final byte[] data;
        private int pos = 0;
        private final WriterDocument document;

        private final Map<Integer, String> fontTable = new HashMap<>();
        private final List<Color> colorTable = new ArrayList<>();
        private int defaultFontIdx = 0;

        // Current paragraph state
        private Paragraph currentParagraph = new Paragraph();
        private final StringBuilder runText = new StringBuilder();

        // Active table state
        private DocumentTable activeTable = null;
        private DocumentTableRow activeRow = null;
        private DocumentTableCell activeCell = null;
        private boolean inTable = false;

        // Row definition being constructed
        private static class CellDef {
            int rightTwips;
            Color bgColor;
            DocumentTableCell.VAlign vAlign = DocumentTableCell.VAlign.TOP;
            double padTop = 3.0, padBottom = 3.0, padLeft = 5.4, padRight = 5.4;
            boolean borderTop = true, borderBottom = true, borderLeft = true, borderRight = true;
        }

        private final List<CellDef> rowCellDefs = new ArrayList<>();
        private CellDef currentCellDef = new CellDef();
        private DocumentTable.Alignment rowAlign = DocumentTable.Alignment.LEFT;
        private int cellIndex = 0;

        // Style state stack
        private static class State {
            int fontIdx = 0;
            double fontSize = 11.0;
            boolean bold = false;
            boolean italic = false;
            boolean underline = false;
            boolean strike = false;
            int colorIdx = 0;
            int highlightIdx = 0;
            Paragraph.Alignment alignment = Paragraph.Alignment.LEFT;
            double spaceBefore = 0;
            double spaceAfter = 0;
            double lineSpacing = 1.0;
            double leftIndent = 0;
            double firstLineIndent = 0;
            boolean skipDestination = false;

            State copy() {
                State s = new State();
                s.fontIdx = this.fontIdx;
                s.fontSize = this.fontSize;
                s.bold = this.bold;
                s.italic = this.italic;
                s.underline = this.underline;
                s.strike = this.strike;
                s.colorIdx = this.colorIdx;
                s.highlightIdx = this.highlightIdx;
                s.alignment = this.alignment;
                s.spaceBefore = this.spaceBefore;
                s.spaceAfter = this.spaceAfter;
                s.lineSpacing = this.lineSpacing;
                s.leftIndent = this.leftIndent;
                s.firstLineIndent = this.firstLineIndent;
                s.skipDestination = this.skipDestination;
                return s;
            }
        }

        private final Deque<State> stateStack = new ArrayDeque<>();
        private State state = new State();

        Parser(byte[] data, WriterDocument document) {
            this.data = data;
            this.document = document;
            // Color table index 0 is auto/black
            colorTable.add(Color.BLACK);
        }

        void parse() {
            while (pos < data.length) {
                byte b = data[pos++];
                if (b == '{') {
                    stateStack.push(state.copy());
                } else if (b == '}') {
                    if (!stateStack.isEmpty()) {
                        state = stateStack.pop();
                    }
                } else if (b == '\\') {
                    parseControlWordOrEscape();
                } else if (b == '\r' || b == '\n') {
                    // Ignored in RTF unless inside a destination
                } else {
                    if (!state.skipDestination) {
                        runText.append((char) (b & 0xFF));
                    }
                }
            }

            flushRun();
            flushParagraph();
            flushTable();

            // Set document default font
            String defFont = fontTable.getOrDefault(defaultFontIdx, "Segoe UI");
            if (defFont.contains(";")) defFont = defFont.replace(";", "").trim();
            document.setDefaultFontFamily(defFont.isBlank() ? "Segoe UI" : defFont);
            document.setDefaultFontSize(11.0);
        }

        private void parseControlWordOrEscape() {
            if (pos >= data.length) return;
            byte b = data[pos++];

            if (b == '\\' || b == '{' || b == '}') {
                if (!state.skipDestination) runText.append((char) b);
                return;
            }

            if (b == '~') {
                if (!state.skipDestination) runText.append('\u00A0'); // Non-breaking space
                return;
            }
            if (b == '_') {
                if (!state.skipDestination) runText.append('\u2011'); // Non-breaking hyphen
                return;
            }

            if (b == '\'') {
                // Hex character: \'hh
                if (pos + 1 < data.length) {
                    int h1 = Character.digit(data[pos++], 16);
                    int h2 = Character.digit(data[pos++], 16);
                    if (h1 >= 0 && h2 >= 0) {
                        int code = (h1 << 4) | h2;
                        if (!state.skipDestination) runText.append((char) code);
                    }
                }
                return;
            }

            if (b == '*') {
                // Ignorable destination: \*
                state.skipDestination = true;
                return;
            }

            // Word name
            StringBuilder word = new StringBuilder();
            word.append((char) b);
            while (pos < data.length && Character.isLetter((char) data[pos])) {
                word.append((char) data[pos++]);
            }

            // Optional integer parameter
            boolean hasParam = false;
            boolean neg = false;
            int param = 0;
            if (pos < data.length && data[pos] == '-') {
                neg = true;
                pos++;
            }
            while (pos < data.length && Character.isDigit((char) data[pos])) {
                hasParam = true;
                param = param * 10 + (data[pos++] - '0');
            }
            if (neg) param = -param;

            // Space delimiter after control word
            if (pos < data.length && data[pos] == ' ') {
                pos++;
            }

            handleControlWord(word.toString(), hasParam, param);
        }

        private void handleControlWord(String word, boolean hasParam, int param) {
            switch (word) {
                // Header tables
                case "fonttbl" -> parseFontTable();
                case "colortbl" -> parseColorTable();
                case "deff" -> defaultFontIdx = param;

                // Destinations to skip
                case "stylesheet", "info", "pict", "generator", "header", "footer", "xmlopen" -> {
                    state.skipDestination = true;
                }

                // Paragraph break
                case "par" -> {
                    if (state.skipDestination) return;
                    flushRun();
                    flushParagraph();
                }

                // Page break
                case "page" -> {
                    if (state.skipDestination) return;
                    flushRun();
                    flushParagraph();
                    flushTable();
                    document.addElement(new PageBreakElement());
                }

                // Tab and line break
                case "tab" -> {
                    if (!state.skipDestination) runText.append('\t');
                }
                case "line" -> {
                    if (!state.skipDestination) runText.append('\n');
                }

                // Character formatting
                case "f" -> {
                    flushRun();
                    state.fontIdx = param;
                }
                case "fs" -> {
                    flushRun();
                    state.fontSize = param / 2.0;
                }
                case "b" -> {
                    flushRun();
                    state.bold = !hasParam || param != 0;
                }
                case "i" -> {
                    flushRun();
                    state.italic = !hasParam || param != 0;
                }
                case "ul" -> {
                    flushRun();
                    state.underline = true;
                }
                case "ulnone" -> {
                    flushRun();
                    state.underline = false;
                }
                case "strike" -> {
                    flushRun();
                    state.strike = !hasParam || param != 0;
                }
                case "cf" -> {
                    flushRun();
                    state.colorIdx = param;
                }
                case "highlight", "cb" -> {
                    flushRun();
                    state.highlightIdx = param;
                }

                // Paragraph formatting
                case "pard" -> {
                    state.alignment = Paragraph.Alignment.LEFT;
                    state.spaceBefore = 0;
                    state.spaceAfter = 0;
                    state.lineSpacing = 1.0;
                    state.leftIndent = 0;
                    state.firstLineIndent = 0;
                    inTable = false;
                }
                case "qc" -> state.alignment = Paragraph.Alignment.CENTER;
                case "qr" -> state.alignment = Paragraph.Alignment.RIGHT;
                case "qj" -> state.alignment = Paragraph.Alignment.JUSTIFY;
                case "ql" -> state.alignment = Paragraph.Alignment.LEFT;
                case "sb" -> state.spaceBefore = param / 20.0;
                case "sa" -> state.spaceAfter = param / 20.0;
                case "sl" -> {
                    if (param > 0) state.lineSpacing = (param / 20.0) / 12.0;
                }
                case "li" -> state.leftIndent = param / 20.0;
                case "fi" -> state.firstLineIndent = param / 20.0;

                // Table row definitions
                case "trowd" -> {
                    flushRun();
                    flushParagraph();
                    rowCellDefs.clear();
                    currentCellDef = new CellDef();
                    rowAlign = DocumentTable.Alignment.LEFT;
                    cellIndex = 0;
                    if (activeTable == null) {
                        activeTable = new DocumentTable();
                        activeTable.getRows().clear();
                    }
                }
                case "trqc" -> rowAlign = DocumentTable.Alignment.CENTER;
                case "trqr" -> rowAlign = DocumentTable.Alignment.RIGHT;
                case "trql" -> rowAlign = DocumentTable.Alignment.LEFT;

                // Cell definitions
                case "clcbpat" -> {
                    if (param > 0 && param < colorTable.size()) {
                        currentCellDef.bgColor = colorTable.get(param);
                    }
                }
                case "clvertalc" -> currentCellDef.vAlign = DocumentTableCell.VAlign.CENTER;
                case "clvertalb" -> currentCellDef.vAlign = DocumentTableCell.VAlign.BOTTOM;
                case "clvertalt" -> currentCellDef.vAlign = DocumentTableCell.VAlign.TOP;
                case "clpadt" -> currentCellDef.padTop = param / 20.0;
                case "clpadb" -> currentCellDef.padBottom = param / 20.0;
                case "clpadl" -> currentCellDef.padLeft = param / 20.0;
                case "clpadr" -> currentCellDef.padRight = param / 20.0;
                case "cellx" -> {
                    currentCellDef.rightTwips = param;
                    rowCellDefs.add(currentCellDef);
                    currentCellDef = new CellDef();
                }

                // Table content
                case "intbl" -> {
                    inTable = true;
                    if (activeRow == null) {
                        activeRow = new DocumentTableRow();
                    }
                    if (activeCell == null) {
                        activeCell = new DocumentTableCell();
                        activeCell.getParagraphs().clear();
                        if (cellIndex < rowCellDefs.size()) {
                            applyCellDef(activeCell, rowCellDefs.get(cellIndex));
                        }
                    }
                }
                case "cell" -> {
                    flushRun();
                    if (activeCell != null) {
                        if (!currentParagraph.getRuns().isEmpty() || !currentParagraph.getPlainText().isEmpty()) {
                            activeCell.addParagraph(currentParagraph);
                            currentParagraph = new Paragraph();
                        }
                        if (activeCell.getParagraphs().isEmpty()) {
                            activeCell.addParagraph(new Paragraph());
                        }
                        if (activeRow != null) {
                            activeRow.addCell(activeCell);
                        }
                        activeCell = null;
                    }
                    cellIndex++;
                    if (cellIndex < rowCellDefs.size()) {
                        activeCell = new DocumentTableCell();
                        activeCell.getParagraphs().clear();
                        applyCellDef(activeCell, rowCellDefs.get(cellIndex));
                    }
                }
                case "row" -> {
                    flushRun();
                    if (activeRow != null) {
                        if (activeTable != null) {
                            activeTable.setAlignment(rowAlign);
                            activeTable.addRow(activeRow);
                        }
                        activeRow = null;
                    }
                    activeCell = null;
                    cellIndex = 0;
                }
            }
        }

        private void applyCellDef(DocumentTableCell cell, CellDef def) {
            if (def.bgColor != null) cell.setBackgroundColor(def.bgColor);
            cell.setVerticalAlignment(def.vAlign);
            cell.setPaddingTop(def.padTop);
            cell.setPaddingBottom(def.padBottom);
            cell.setPaddingLeft(def.padLeft);
            cell.setPaddingRight(def.padRight);
            cell.setBorderTop(def.borderTop);
            cell.setBorderBottom(def.borderBottom);
            cell.setBorderLeft(def.borderLeft);
            cell.setBorderRight(def.borderRight);
        }

        private void flushRun() {
            if (runText.length() == 0 || state.skipDestination) return;

            String font = fontTable.getOrDefault(state.fontIdx, "Segoe UI");
            if (font.contains(";")) font = font.replace(";", "").trim();
            if (font.isBlank()) font = "Segoe UI";

            Color color = (state.colorIdx > 0 && state.colorIdx < colorTable.size()) ?
                    colorTable.get(state.colorIdx) : Color.BLACK;

            Color highlight = (state.highlightIdx > 0 && state.highlightIdx < colorTable.size()) ?
                    colorTable.get(state.highlightIdx) : null;

            TextRun run = new TextRun(runText.toString(), font, state.fontSize,
                    state.bold, state.italic, state.underline, state.strike, color, highlight);

            currentParagraph.addRun(run);
            runText.setLength(0);
        }

        private void flushParagraph() {
            if (currentParagraph.getRuns().isEmpty() && currentParagraph.getPlainText().isEmpty()) {
                return;
            }
            currentParagraph.setAlignment(state.alignment);
            currentParagraph.setSpaceBefore(state.spaceBefore);
            currentParagraph.setSpaceAfter(state.spaceAfter);
            currentParagraph.setLineSpacing(state.lineSpacing);
            currentParagraph.setLeftIndent(state.leftIndent);
            currentParagraph.setFirstLineIndent(state.firstLineIndent);

            if (inTable && activeCell != null) {
                activeCell.addParagraph(currentParagraph);
            } else {
                flushTable();
                document.addElement(currentParagraph);
            }
            currentParagraph = new Paragraph();
        }

        private void flushTable() {
            if (activeTable != null && !activeTable.getRows().isEmpty()) {
                document.addElement(activeTable);
            }
            activeTable = null;
            activeRow = null;
            activeCell = null;
            inTable = false;
        }

        private void parseFontTable() {
            // Read fonts until closing bracket of font table
            int depth = 1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (pos < data.length && depth > 0) {
                byte b = data[pos++];
                if (b == '{') depth++;
                else if (b == '}') {
                    depth--;
                    if (depth == 0) break;
                }
                baos.write(b);
            }
            String tableContent = baos.toString(StandardCharsets.US_ASCII);
            // Formats: {\f0\fnil\fcharset0 Calibri;}
            String[] entries = tableContent.split("\\{\\\\f");
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                int spaceIdx = entry.indexOf(' ');
                int semiIdx = entry.indexOf(';');
                if (semiIdx > 0) {
                    String idPart = entry.substring(0, spaceIdx > 0 ? spaceIdx : entry.indexOf('\\'));
                    int fId = 0;
                    try {
                        fId = Integer.parseInt(idPart.replaceAll("\\D", ""));
                    } catch (Exception ignored) {}

                    String namePart = entry.substring(spaceIdx > 0 ? spaceIdx : 0, semiIdx);
                    // Filter font properties
                    int lastBackslash = namePart.lastIndexOf('\\');
                    if (lastBackslash >= 0) {
                        int fontNameStart = namePart.indexOf(' ', lastBackslash);
                        if (fontNameStart >= 0) namePart = namePart.substring(fontNameStart);
                    }
                    fontTable.put(fId, namePart.trim());
                }
            }
        }

        private void parseColorTable() {
            // Read until closing bracket of colortbl
            int depth = 1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (pos < data.length && depth > 0) {
                byte b = data[pos++];
                if (b == '{') depth++;
                else if (b == '}') {
                    depth--;
                    if (depth == 0) break;
                }
                baos.write(b);
            }
            String content = baos.toString(StandardCharsets.US_ASCII);
            String[] entries = content.split(";");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                int r = 0, g = 0, b = 0;
                try {
                    int rIdx = entry.indexOf("\\red");
                    int gIdx = entry.indexOf("\\green");
                    int bIdx = entry.indexOf("\\blue");
                    if (rIdx >= 0 && gIdx >= 0 && bIdx >= 0) {
                        r = Integer.parseInt(entry.substring(rIdx + 4, gIdx).trim());
                        g = Integer.parseInt(entry.substring(gIdx + 6, bIdx).trim());
                        String bSub = entry.substring(bIdx + 5);
                        int end = 0;
                        while (end < bSub.length() && Character.isDigit(bSub.charAt(end))) end++;
                        b = Integer.parseInt(bSub.substring(0, end).trim());
                        colorTable.add(Color.rgb(r, g, b));
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
