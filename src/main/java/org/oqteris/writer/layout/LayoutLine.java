package org.oqteris.writer.layout;

import javafx.scene.text.Font;
import org.oqteris.writer.model.Paragraph;
import org.oqteris.writer.model.TextRun;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual laid-out line of text with position and runs.
 */
public class LayoutLine {
    public static class LineSegment {
        private final String text;
        private final TextRun run;
        private final Font font;
        private final double width;
        private double x;

        public LineSegment(String text, TextRun run, Font font, double width, double x) {
            this.text = text;
            this.run = run;
            this.font = font;
            this.width = width;
            this.x = x;
        }

        public String getText() {
            return text;
        }

        public TextRun getRun() {
            return run;
        }

        public Font getFont() {
            return font;
        }

        public double getWidth() {
            return width;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }
    }

    private final Paragraph paragraph;
    private final List<LineSegment> segments = new ArrayList<>();
    private double x;
    private double y;
    private double width;
    private double height;
    private double baseline;
    private boolean lastLineOfParagraph = false;

    public LayoutLine(Paragraph paragraph, double x, double y, double height, double baseline) {
        this.paragraph = paragraph;
        this.x = x;
        this.y = y;
        this.height = height;
        this.baseline = baseline;
    }

    public Paragraph getParagraph() {
        return paragraph;
    }

    public List<LineSegment> getSegments() {
        return segments;
    }

    public void addSegment(LineSegment segment) {
        segments.add(segment);
        width += segment.getWidth();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getBaseline() {
        return baseline;
    }

    public boolean isLastLineOfParagraph() {
        return lastLineOfParagraph;
    }

    public void setLastLineOfParagraph(boolean lastLineOfParagraph) {
        this.lastLineOfParagraph = lastLineOfParagraph;
    }

    public String getPlainText() {
        StringBuilder sb = new StringBuilder();
        for (LineSegment s : segments) {
            sb.append(s.getText());
        }
        return sb.toString();
    }
}
