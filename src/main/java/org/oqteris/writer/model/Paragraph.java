package org.oqteris.writer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paragraph element containing text runs with paragraph-level formatting.
 */
public class Paragraph implements DocumentElement {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }

    public enum HeadingStyle {
        NORMAL,
        TITLE,
        SUBTITLE,
        HEADING_1,
        HEADING_2,
        HEADING_3
    }

    public enum ListType {
        NONE,
        BULLET,
        NUMBERED
    }

    private final List<TextRun> runs = new ArrayList<>();
    private Alignment alignment = Alignment.LEFT;
    private HeadingStyle headingStyle = HeadingStyle.NORMAL;
    private ListType listType = ListType.NONE;
    private int listLevel = 0;
    private int listIndex = 1; // For numbered lists

    private double lineSpacing = 1.15;
    private double spaceBefore = 0.0;
    private double spaceAfter = 6.0;
    private double leftIndent = 0.0;
    private double rightIndent = 0.0;
    private double firstLineIndent = 0.0;

    public Paragraph() {
    }

    public Paragraph(String initialText) {
        if (initialText != null && !initialText.isEmpty()) {
            runs.add(new TextRun(initialText));
        }
    }

    @Override
    public ElementType getElementType() {
        return ElementType.PARAGRAPH;
    }

    public List<TextRun> getRuns() {
        return runs;
    }

    public void addRun(TextRun run) {
        if (run != null) {
            runs.add(run);
        }
    }

    public void addText(String text) {
        if (text != null && !text.isEmpty()) {
            if (!runs.isEmpty()) {
                TextRun last = runs.get(runs.size() - 1);
                last.setText(last.getText() + text);
            } else {
                runs.add(new TextRun(text));
            }
        }
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
    }

    public HeadingStyle getHeadingStyle() {
        return headingStyle;
    }

    public void setHeadingStyle(HeadingStyle headingStyle) {
        this.headingStyle = headingStyle != null ? headingStyle : HeadingStyle.NORMAL;
        applyHeadingDefaults();
    }

    private void applyHeadingDefaults() {
        switch (headingStyle) {
            case TITLE -> {
                setSpaceBefore(12);
                setSpaceAfter(12);
                for (TextRun r : runs) {
                    r.setFontSize(26);
                    r.setBold(true);
                }
            }
            case HEADING_1 -> {
                setSpaceBefore(12);
                setSpaceAfter(6);
                for (TextRun r : runs) {
                    r.setFontSize(18);
                    r.setBold(true);
                }
            }
            case HEADING_2 -> {
                setSpaceBefore(10);
                setSpaceAfter(4);
                for (TextRun r : runs) {
                    r.setFontSize(14);
                    r.setBold(true);
                }
            }
            case HEADING_3 -> {
                setSpaceBefore(8);
                setSpaceAfter(2);
                for (TextRun r : runs) {
                    r.setFontSize(12);
                    r.setBold(true);
                }
            }
            case NORMAL -> {
                setSpaceBefore(0);
                setSpaceAfter(6);
            }
        }
    }

    public ListType getListType() {
        return listType;
    }

    public void setListType(ListType listType) {
        this.listType = listType != null ? listType : ListType.NONE;
    }

    public int getListLevel() {
        return listLevel;
    }

    public void setListLevel(int listLevel) {
        this.listLevel = Math.max(0, listLevel);
    }

    public int getListIndex() {
        return listIndex;
    }

    public void setListIndex(int listIndex) {
        this.listIndex = Math.max(1, listIndex);
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(double lineSpacing) {
        this.lineSpacing = Math.max(0.8, lineSpacing);
    }

    public double getSpaceBefore() {
        return spaceBefore;
    }

    public void setSpaceBefore(double spaceBefore) {
        this.spaceBefore = Math.max(0, spaceBefore);
    }

    public double getSpaceAfter() {
        return spaceAfter;
    }

    public void setSpaceAfter(double spaceAfter) {
        this.spaceAfter = Math.max(0, spaceAfter);
    }

    public double getLeftIndent() {
        return leftIndent;
    }

    public void setLeftIndent(double leftIndent) {
        this.leftIndent = Math.max(0, leftIndent);
    }

    public double getRightIndent() {
        return rightIndent;
    }

    public void setRightIndent(double rightIndent) {
        this.rightIndent = Math.max(0, rightIndent);
    }

    public double getFirstLineIndent() {
        return firstLineIndent;
    }

    public void setFirstLineIndent(double firstLineIndent) {
        this.firstLineIndent = firstLineIndent;
    }

    public String getPlainText() {
        StringBuilder sb = new StringBuilder();
        for (TextRun r : runs) {
            sb.append(r.getText());
        }
        return sb.toString();
    }

    public int getWordCount() {
        String text = getPlainText().trim();
        if (text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }

    public int getCharacterCount() {
        return getPlainText().length();
    }
}
