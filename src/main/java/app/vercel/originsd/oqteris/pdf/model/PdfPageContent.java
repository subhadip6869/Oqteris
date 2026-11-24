package app.vercel.originsd.oqteris.pdf.model;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import app.vercel.originsd.oqteris.crypto.signature.PdfSignatureInfo;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Encapsulates the extracted layout of a PDF page, including line-by-line text geometry,
 * interactive objects (images, signatures, annotations, form fields), and spatial selection logic.
 */
public class PdfPageContent {

    public enum ObjectType {
        IMAGE,
        SHAPE,
        SIGNATURE,
        ANNOTATION,
        FORM_FIELD
    }

    public static class CharBox {
        public final char character;
        public final double normX;
        public final double normW;

        public CharBox(char character, double normX, double normW) {
            this.character = character;
            this.normX = normX;
            this.normW = normW;
        }
    }

    public static class WordBox {
        public final String word;
        public final double normX;
        public final double normY;
        public final double normW;
        public final double normH;

        public WordBox(String word, double normX, double normY, double normW, double normH) {
            this.word = word;
            this.normX = normX;
            this.normY = normY;
            this.normW = normW;
            this.normH = normH;
        }

        public boolean contains(double x, double y) {
            return x >= normX && x <= normX + normW && y >= normY && y <= normY + normH;
        }
    }

    public static class TextLine {
        public final String text;
        public final double normX;
        public final double normY;
        public final double normW;
        public final double normH;
        public final List<CharBox> charBoxes;
        public final List<WordBox> words;

        public TextLine(String text, double normX, double normY, double normW, double normH,
                        List<CharBox> charBoxes, List<WordBox> words) {
            this.text = text;
            this.normX = normX;
            this.normY = normY;
            this.normW = normW;
            this.normH = normH;
            this.charBoxes = charBoxes != null ? charBoxes : Collections.emptyList();
            this.words = words != null ? words : Collections.emptyList();
        }

        public boolean contains(double x, double y) {
            return x >= normX - 0.005 && x <= normX + normW + 0.005
                    && y >= normY - 0.002 && y <= normY + normH + 0.002;
        }
    }

    public static class PageObject {
        public final ObjectType type;
        public final double normX;
        public final double normY;
        public final double normW;
        public final double normH;
        public final String title;
        public final Object payload;

        public PageObject(ObjectType type, double normX, double normY, double normW, double normH,
                          String title, Object payload) {
            this.type = type;
            this.normX = Math.max(0.0, normX);
            this.normY = Math.max(0.0, normY);
            this.normW = Math.max(0.0, normW);
            this.normH = Math.max(0.0, normH);
            this.title = title != null ? title : type.name();
            this.payload = payload;
        }

        public boolean contains(double x, double y) {
            return x >= normX && x <= normX + normW && y >= normY && y <= normY + normH;
        }

        public double[] getBounds() {
            return new double[]{ normX, normY, normW, normH };
        }
    }

    public static class SelectionResult {
        public final List<double[]> lineRects;
        public final String text;

        public SelectionResult(List<double[]> lineRects, String text) {
            this.lineRects = lineRects != null ? lineRects : Collections.emptyList();
            this.text = text != null ? text : "";
        }

        public boolean isEmpty() {
            return lineRects.isEmpty();
        }
    }

    private final int pageIndex;
    private final double pageWidth;
    private final double pageHeight;
    private final List<TextLine> textLines;
    private final List<PageObject> pageObjects;

    public PdfPageContent(int pageIndex, double pageWidth, double pageHeight,
                          List<TextLine> textLines, List<PageObject> pageObjects) {
        this.pageIndex = pageIndex;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.textLines = textLines != null ? textLines : Collections.emptyList();
        this.pageObjects = pageObjects != null ? pageObjects : Collections.emptyList();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public List<TextLine> getTextLines() {
        return Collections.unmodifiableList(textLines);
    }

    public List<PageObject> getPageObjects() {
        return Collections.unmodifiableList(pageObjects);
    }

    /**
     * Hit tests all clickable objects on this page (images, shapes, signatures, annotations).
     * If the coordinates hit a text line, returns null because lines of text are text, NOT objects.
     */
    public PageObject hitTestObject(double normX, double normY) {
        // Text lines are text, NEVER objects. If mouse is over text, do not select as object.
        if (hitTestLine(normX, normY) != null) {
            return null;
        }

        // Hit-test in reverse order so topmost objects take priority
        for (int i = pageObjects.size() - 1; i >= 0; i--) {
            PageObject obj = pageObjects.get(i);
            if (obj.contains(normX, normY)) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Hit tests text line at normalized coordinates.
     */
    public TextLine hitTestLine(double normX, double normY) {
        for (TextLine line : textLines) {
            if (line.contains(normX, normY)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Hit tests a specific word at normalized coordinates (for double click selection).
     */
    public WordBox hitTestWord(double normX, double normY) {
        for (TextLine line : textLines) {
            if (normY >= line.normY - 0.003 && normY <= line.normY + line.normH + 0.003) {
                for (WordBox wb : line.words) {
                    if (wb.contains(normX, normY)) {
                        return wb;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Computes line-by-line selection highlights between start and end normalized coordinates.
     */
    public SelectionResult computeSelection(double startX, double startY, double endX, double endY) {
        if (textLines.isEmpty()) {
            return new SelectionResult(Collections.emptyList(), "");
        }

        // Determine chronological order (top-to-bottom, left-to-right)
        boolean forward = (startY < endY) || (Math.abs(startY - endY) < 0.015 && startX <= endX);
        double fromX = forward ? startX : endX;
        double fromY = forward ? startY : endY;
        double toX = forward ? endX : startX;
        double toY = forward ? endY : startY;

        List<double[]> rects = new ArrayList<>();
        StringBuilder textBuf = new StringBuilder();

        // Find which lines are within the vertical selection span
        List<TextLine> selectedLines = new ArrayList<>();
        for (TextLine line : textLines) {
            double lineTop = line.normY;
            double lineBottom = line.normY + line.normH;

            // Expand line vertical bounds slightly to catch drags that start slightly above or below
            double padY = line.normH * 0.4;
            if (lineBottom < fromY - padY) {
                continue; // Line is completely above selection
            }
            if (lineTop > toY + padY) {
                continue; // Line is completely below selection
            }
            selectedLines.add(line);
        }

        if (selectedLines.isEmpty()) {
            return new SelectionResult(Collections.emptyList(), "");
        }

        if (selectedLines.size() == 1) {
            // Single line selection
            TextLine line = selectedLines.get(0);
            double minSelX = Math.min(fromX, toX);
            double maxSelX = Math.max(fromX, toX);

            double selLeft = Math.max(line.normX, minSelX);
            double selRight = Math.min(line.normX + line.normW, maxSelX);

            if (selRight > selLeft + 0.001) {
                rects.add(new double[]{ selLeft, line.normY, selRight - selLeft, line.normH });
                String sub = extractLineSubText(line, selLeft, selRight);
                if (!sub.isEmpty()) {
                    textBuf.append(sub);
                }
            }
        } else {
            // Multi-line selection
            for (int i = 0; i < selectedLines.size(); i++) {
                TextLine line = selectedLines.get(i);
                double selLeft;
                double selRight;

                if (i == 0) {
                    // First line: starts at fromX, extends to line end
                    selLeft = Math.max(line.normX, fromX);
                    selRight = line.normX + line.normW;
                } else if (i == selectedLines.size() - 1) {
                    // Last line: starts at line start, extends to toX
                    selLeft = line.normX;
                    selRight = Math.min(line.normX + line.normW, toX);
                } else {
                    // Intermediate lines: entire line width
                    selLeft = line.normX;
                    selRight = line.normX + line.normW;
                }

                if (selRight > selLeft + 0.001) {
                    rects.add(new double[]{ selLeft, line.normY, selRight - selLeft, line.normH });
                    String sub = extractLineSubText(line, selLeft, selRight);
                    if (!sub.isEmpty()) {
                        if (textBuf.length() > 0) textBuf.append("\n");
                        textBuf.append(sub);
                    }
                }
            }
        }

        return new SelectionResult(rects, textBuf.toString());
    }

    private static String extractLineSubText(TextLine line, double selLeft, double selRight) {
        if (line.charBoxes.isEmpty()) {
            // Proportional estimate if no character boxes
            if (line.normW <= 0) return line.text;
            double r1 = Math.max(0.0, Math.min(1.0, (selLeft - line.normX) / line.normW));
            double r2 = Math.max(0.0, Math.min(1.0, (selRight - line.normX) / line.normW));
            int idx1 = (int) Math.round(r1 * line.text.length());
            int idx2 = (int) Math.round(r2 * line.text.length());
            idx1 = Math.max(0, Math.min(line.text.length(), idx1));
            idx2 = Math.max(idx1, Math.min(line.text.length(), idx2));
            return line.text.substring(idx1, idx2).trim();
        }

        StringBuilder sb = new StringBuilder();
        for (CharBox cb : line.charBoxes) {
            double charMid = cb.normX + (cb.normW / 2.0);
            if (charMid >= selLeft && charMid <= selRight) {
                sb.append(cb.character);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Extracts text lines, character bounds, and page objects (images, signatures, annotations)
     * from a given PDPage.
     */
    public static PdfPageContent extract(PDDocument doc, int pageIndex,
                                         List<PdfSignatureInfo> signatures,
                                         List<PdfAnnotation> annotations) {
        if (doc == null || pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
            return new PdfPageContent(pageIndex, 1.0, 1.0, Collections.emptyList(), Collections.emptyList());
        }

        PDPage page = doc.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        if (mediaBox == null) mediaBox = PDRectangle.A4;
        double pageW = mediaBox.getWidth();
        double pageH = mediaBox.getHeight();

        List<TextLine> lines = new ArrayList<>();
        List<PageObject> objects = new ArrayList<>();

        // 1. Extract Text Lines and Words using PDFTextStripper
        try {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    if (textPositions != null && !textPositions.isEmpty() && text != null && !text.isBlank()) {
                        TextPosition first = textPositions.get(0);
                        TextPosition last = textPositions.get(textPositions.size() - 1);

                        double minX = first.getXDirAdj();
                        double maxX = last.getXDirAdj() + last.getWidthDirAdj();
                        double minY = first.getYDirAdj() - first.getHeightDir();
                        double maxH = first.getHeightDir();

                        List<CharBox> chars = new ArrayList<>();
                        for (TextPosition tp : textPositions) {
                            String u = tp.getUnicode();
                            char ch = (u != null && !u.isEmpty()) ? u.charAt(0) : ' ';
                            double cx = tp.getXDirAdj() / pageW;
                            double cw = tp.getWidthDirAdj() / pageW;
                            chars.add(new CharBox(ch, cx, cw));
                            minX = Math.min(minX, tp.getXDirAdj());
                            maxX = Math.max(maxX, tp.getXDirAdj() + tp.getWidthDirAdj());
                            minY = Math.min(minY, tp.getYDirAdj() - tp.getHeightDir());
                            maxH = Math.max(maxH, tp.getHeightDir());
                        }

                        double normX = Math.max(0.0, minX / pageW);
                        double normY = Math.max(0.0, minY / pageH);
                        double normW = Math.max(0.0, (maxX - minX) / pageW);
                        double normH = Math.max(0.0, maxH / pageH);

                        // Extract words inside the line
                        List<WordBox> words = new ArrayList<>();
                        String[] wordTokens = text.split("\\s+");
                        int charIdx = 0;
                        for (String token : wordTokens) {
                            if (token.isEmpty()) continue;
                            int startToken = text.indexOf(token, charIdx);
                            if (startToken >= 0 && startToken < chars.size()) {
                                int endToken = Math.min(chars.size() - 1, startToken + token.length() - 1);
                                double wx = chars.get(startToken).normX;
                                double we = chars.get(endToken).normX + chars.get(endToken).normW;
                                words.add(new WordBox(token, wx, normY, Math.max(0.0, we - wx), normH));
                                charIdx = startToken + token.length();
                            }
                        }

                        lines.add(new TextLine(text.trim(), normX, normY, normW, normH, chars, words));
                    }
                    super.writeString(text, textPositions);
                }
            };
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.setSortByPosition(true);
            stripper.getText(doc);
        } catch (Exception ignored) {
        }

        // Sort lines top-down, left-to-right
        lines.sort(Comparator.comparingDouble((TextLine l) -> l.normY).thenComparingDouble(l -> l.normX));

        // 2. Extract Images and Vector Shapes using PDFGraphicsStreamEngine
        try {
            PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(page) {
                private double pathMinX = Double.MAX_VALUE;
                private double pathMinY = Double.MAX_VALUE;
                private double pathMaxX = -Double.MAX_VALUE;
                private double pathMaxY = -Double.MAX_VALUE;
                private boolean hasPath = false;

                private void updateBounds(float x, float y) {
                    Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                    Point2D.Float pt = ctm.transformPoint(x, y);
                    pathMinX = Math.min(pathMinX, pt.getX());
                    pathMinY = Math.min(pathMinY, pt.getY());
                    pathMaxX = Math.max(pathMaxX, pt.getX());
                    pathMaxY = Math.max(pathMaxY, pt.getY());
                    hasPath = true;
                }

                @Override
                public void drawImage(PDImage pdImage) {
                    Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                    double imgX = ctm.getTranslateX();
                    double imgY = ctm.getTranslateY();
                    double imgW = ctm.getScalingFactorX();
                    double imgH = ctm.getScalingFactorY();

                    if (imgW > 2 && imgH > 2) {
                        double normX = imgX / pageW;
                        double normY = (pageH - (imgY + imgH)) / pageH;
                        double normW = imgW / pageW;
                        double normH = imgH / pageH;
                        String desc = String.format("Image (%dx%d)", pdImage.getWidth(), pdImage.getHeight());
                        objects.add(new PageObject(ObjectType.IMAGE, normX, normY, normW, normH, desc, pdImage));
                    }
                }

                @Override
                public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
                    updateBounds((float) p0.getX(), (float) p0.getY());
                    updateBounds((float) p1.getX(), (float) p1.getY());
                    updateBounds((float) p2.getX(), (float) p2.getY());
                    updateBounds((float) p3.getX(), (float) p3.getY());
                }

                @Override public void moveTo(float x, float y) { updateBounds(x, y); }
                @Override public void lineTo(float x, float y) { updateBounds(x, y); }
                @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
                    updateBounds(x1, y1); updateBounds(x2, y2); updateBounds(x3, y3);
                }

                private void completePath() {
                    if (hasPath) {
                        double w = pathMaxX - pathMinX;
                        double h = pathMaxY - pathMinY;
                        // Avoid full-page backgrounds and tiny line artifacts
                        if (w >= 10 && h >= 10 && (w < pageW * 0.98 || h < pageH * 0.98)) {
                            double normX = Math.max(0.0, pathMinX / pageW);
                            double normY = Math.max(0.0, (pageH - pathMaxY) / pageH);
                            double normW = Math.max(0.0, w / pageW);
                            double normH = Math.max(0.0, h / pageH);
                            String desc = String.format("Shape (%dx%d)", (int) w, (int) h);
                            objects.add(new PageObject(ObjectType.SHAPE, normX, normY, normW, normH, desc, null));
                        }
                    }
                    pathMinX = Double.MAX_VALUE;
                    pathMinY = Double.MAX_VALUE;
                    pathMaxX = -Double.MAX_VALUE;
                    pathMaxY = -Double.MAX_VALUE;
                    hasPath = false;
                }

                @Override public void strokePath() { completePath(); }
                @Override public void fillPath(int windingRule) { completePath(); }
                @Override public void fillAndStrokePath(int windingRule) { completePath(); }
                @Override public void closePath() {}
                @Override public void endPath() { hasPath = false; }
                @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
                @Override public void clip(int windingRule) {}
                @Override public void shadingFill(COSName shadingName) {}
            };
            engine.processPage(page);
        } catch (Exception ignored) {
        }

        // 3. Extract interactive form field widgets (ignoring text links or text markup)
        try {
            List<PDAnnotation> pageAnnots = page.getAnnotations();
            if (pageAnnots != null) {
                for (PDAnnotation a : pageAnnots) {
                    if (a instanceof org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget) {
                        PDRectangle r = a.getRectangle();
                        if (r != null && r.getWidth() > 2 && r.getHeight() > 2) {
                            double normX = r.getLowerLeftX() / pageW;
                            double normY = (pageH - r.getUpperRightY()) / pageH;
                            double normW = r.getWidth() / pageW;
                            double normH = r.getHeight() / pageH;
                            String name = a.getAnnotationName() != null ? a.getAnnotationName() : "Form Field";
                            objects.add(new PageObject(ObjectType.FORM_FIELD, normX, normY, normW, normH, name, a));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Attach verified Signatures
        if (signatures != null) {
            for (PdfSignatureInfo s : signatures) {
                if (s.isVisual() && s.getVisualRect() != null && s.getPageIndex() == pageIndex) {
                    double[] vr = s.getVisualRect();
                    String title = "Signature: " + s.getSignerName();
                    objects.add(new PageObject(ObjectType.SIGNATURE, vr[0], vr[1], vr[2], vr[3], title, s));
                }
            }
        }

        // 5. Attach User Annotations
        if (annotations != null) {
            for (PdfAnnotation a : annotations) {
                if (a.getPageIndex() == pageIndex) {
                    String title = "Annotation: " + a.getType().name();
                    objects.add(new PageObject(ObjectType.ANNOTATION, a.getX(), a.getY(), a.getWidth(), a.getHeight(), title, a));
                }
            }
        }

        return new PdfPageContent(pageIndex, pageW, pageH, lines, objects);
    }
}
