package org.originsd.oqteris.writer.model;

import org.originsd.oqteris.core.model.AbstractDocumentModel;
import org.originsd.oqteris.core.model.DocumentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Word processor document object model holding structural elements, page layout, and headers/footers.
 */
public class WriterDocument extends AbstractDocumentModel {
    private PageSetup pageSetup = PageSetup.a4();
    private final List<DocumentElement> elements = new ArrayList<>();

    private Paragraph header = null;
    private Paragraph footer = null;

    private String defaultFontFamily = null;
    private double defaultFontSize = 11.0;

    public WriterDocument() {
        this("Document 1");
    }

    public WriterDocument(String defaultTitle) {
        super(DocumentType.WRITER, defaultTitle);
        // Default empty paragraph
        elements.add(new Paragraph());
    }

    public PageSetup getPageSetup() {
        return pageSetup;
    }

    public void setPageSetup(PageSetup pageSetup) {
        this.pageSetup = pageSetup != null ? pageSetup : PageSetup.a4();
        notifyChanged();
    }

    public List<DocumentElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void addElement(DocumentElement element) {
        if (element != null) {
            elements.add(element);
            setModified(true);
            notifyChanged();
        }
    }

    public void insertElement(int index, DocumentElement element) {
        if (element != null && index >= 0 && index <= elements.size()) {
            elements.add(index, element);
            setModified(true);
            notifyChanged();
        }
    }

    public void removeElement(int index) {
        if (index >= 0 && index < elements.size()) {
            elements.remove(index);
            if (elements.isEmpty()) {
                elements.add(new Paragraph());
            }
            setModified(true);
            notifyChanged();
        }
    }

    public void clearElements() {
        elements.clear();
        setModified(true);
        notifyChanged();
    }

    public Paragraph getHeader() {
        return header;
    }

    public void setHeader(Paragraph header) {
        this.header = header;
        setModified(true);
        notifyChanged();
    }

    public Paragraph getFooter() {
        return footer;
    }

    public void setFooter(Paragraph footer) {
        this.footer = footer;
        setModified(true);
        notifyChanged();
    }

    public String getDefaultFontFamily() {
        return defaultFontFamily;
    }

    public void setDefaultFontFamily(String defaultFontFamily) {
        this.defaultFontFamily = defaultFontFamily;
    }

    public double getDefaultFontSize() {
        return defaultFontSize;
    }

    public void setDefaultFontSize(double defaultFontSize) {
        this.defaultFontSize = defaultFontSize > 0 ? defaultFontSize : 11.0;
    }

    /**
     * Detects which font is applied in the document.
     * Uses explicit defaultFontFamily if set, otherwise scans all paragraphs,
     * runs, and tables to determine the most prevalent font family.
     */
    public String getDetectedFontFamily() {
        if (defaultFontFamily != null && !defaultFontFamily.isBlank()) {
            return defaultFontFamily;
        }
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (DocumentElement el : elements) {
            if (el instanceof Paragraph p) {
                tallyParagraphFonts(p, counts);
            } else if (el instanceof DocumentTable t) {
                for (DocumentTableRow row : t.getRows()) {
                    for (DocumentTableCell cell : row.getCells()) {
                        for (Paragraph cp : cell.getParagraphs()) {
                            tallyParagraphFonts(cp, counts);
                        }
                    }
                }
            }
        }
        if (!counts.isEmpty()) {
            return counts.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("Calibri");
        }
        return "Calibri";
    }

    private void tallyParagraphFonts(Paragraph p, java.util.Map<String, Integer> counts) {
        for (TextRun r : p.getRuns()) {
            String f = r.getFontFamily();
            if (f != null && !f.isBlank()) {
                int len = Math.max(1, r.getText().length());
                counts.merge(f, len, Integer::sum);
            }
        }
    }

    public int getWordCount() {
        int words = 0;
        for (DocumentElement el : elements) {
            if (el instanceof Paragraph p) {
                words += p.getWordCount();
            } else if (el instanceof DocumentTable t) {
                for (DocumentTableRow row : t.getRows()) {
                    for (DocumentTableCell cell : row.getCells()) {
                        for (Paragraph cp : cell.getParagraphs()) {
                            words += cp.getWordCount();
                        }
                    }
                }
            }
        }
        return words;
    }

    public int getCharacterCount() {
        int chars = 0;
        for (DocumentElement el : elements) {
            if (el instanceof Paragraph p) {
                chars += p.getCharacterCount();
            } else if (el instanceof DocumentTable t) {
                for (DocumentTableRow row : t.getRows()) {
                    for (DocumentTableCell cell : row.getCells()) {
                        for (Paragraph cp : cell.getParagraphs()) {
                            chars += cp.getCharacterCount();
                        }
                    }
                }
            }
        }
        return chars;
    }

    public int getParagraphCount() {
        int count = 0;
        for (DocumentElement el : elements) {
            if (el instanceof Paragraph) count++;
        }
        return count;
    }

    public String getPlainText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentElement el : elements) {
            if (el instanceof Paragraph p) {
                sb.append(p.getPlainText()).append("\n");
            } else if (el instanceof DocumentTable t) {
                for (DocumentTableRow row : t.getRows()) {
                    List<String> cellTexts = new ArrayList<>();
                    for (DocumentTableCell cell : row.getCells()) {
                        StringBuilder cellSb = new StringBuilder();
                        for (Paragraph cp : cell.getParagraphs()) {
                            cellSb.append(cp.getPlainText()).append(" ");
                        }
                        cellTexts.add(cellSb.toString().trim());
                    }
                    sb.append(String.join("\t", cellTexts)).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
