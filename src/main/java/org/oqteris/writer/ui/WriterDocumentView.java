package org.oqteris.writer.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.ui.DocumentView;
import org.oqteris.writer.layout.LayoutEngine;
import org.oqteris.writer.layout.LayoutLine;
import org.oqteris.writer.layout.LayoutPage;
import org.oqteris.writer.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main UI view and interactive canvas for the Oqteris Writer word processor.
 */
public class WriterDocumentView extends BorderPane implements DocumentView {
    private final WriterDocument document;
    private final List<LayoutPage> pages = new ArrayList<>();

    // Multi-page canvas
    private final Canvas canvas = new Canvas(800, 1000);
    private final ScrollPane scrollPane = new ScrollPane();
    private final StackPane canvasContainer = new StackPane();

    // Ribbon toolbar
    private final HBox ribbonBar = new HBox(6);
    private final Label statusStatsLabel = new Label("Page 1 of 1 | Words: 0");

    // Caret & selection state
    private int currentElementIndex = 0;
    private int currentRunIndex = 0;
    private int currentCharOffset = 0;
    private boolean caretVisible = true;
    private double caretX = 72;
    private double caretY = 72;
    private double caretHeight = 14;
    private Timeline caretBlink;

    public WriterDocumentView() {
        this(new WriterDocument());
    }

    public WriterDocumentView(WriterDocument document) {
        this.document = document;
        buildUI();
        relayout();
        setupCaretBlink();
    }

    private void buildUI() {
        // Canvas Container
        canvasContainer.setAlignment(Pos.TOP_CENTER);
        canvasContainer.setPadding(new Insets(24));
        canvasContainer.setStyle("-fx-background-color: #e8eaed;");
        canvasContainer.getChildren().add(canvas);

        scrollPane.setContent(canvasContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #e8eaed; -fx-background: #e8eaed;");

        setCenter(scrollPane);

        // Canvas events
        canvas.setFocusTraversable(true);
        canvas.setOnMouseClicked(this::handleCanvasClick);
        canvas.setOnKeyPressed(this::handleKeyPressed);
        canvas.setOnKeyTyped(this::handleKeyTyped);

        // Ribbon Toolbar
        setupRibbonToolbar();
    }

    private void setupCaretBlink() {
        caretBlink = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            caretVisible = !caretVisible;
            render();
        }));
        caretBlink.setCycleCount(Animation.INDEFINITE);
        caretBlink.play();
    }

    private void setupRibbonToolbar() {
        ribbonBar.setAlignment(Pos.CENTER_LEFT);
        ribbonBar.setSpacing(6);

        // Style Preset (Normal, Title, Heading 1, 2, 3)
        ComboBox<Paragraph.HeadingStyle> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(Paragraph.HeadingStyle.values());
        styleCombo.setValue(Paragraph.HeadingStyle.NORMAL);
        styleCombo.setPrefWidth(120);
        styleCombo.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setHeadingStyle(styleCombo.getValue());
                document.setModified(true);
                relayout();
            }
        });

        // Font Family
        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("Segoe UI", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia");
        fontCombo.setValue("Segoe UI");
        fontCombo.setPrefWidth(120);
        fontCombo.setOnAction(e -> applyRunStyle(r -> r.setFontFamily(fontCombo.getValue())));

        // Font Size
        ComboBox<Double> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll(9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 24.0, 32.0);
        sizeCombo.setValue(11.0);
        sizeCombo.setPrefWidth(65);
        sizeCombo.setOnAction(e -> applyRunStyle(r -> r.setFontSize(sizeCombo.getValue())));

        // Bold / Italic / Underline / Strikethrough
        ToggleButton boldBtn = new ToggleButton("B");
        boldBtn.setStyle("-fx-font-weight: bold;");
        boldBtn.setOnAction(e -> applyRunStyle(r -> r.setBold(boldBtn.isSelected())));

        ToggleButton italicBtn = new ToggleButton("I");
        italicBtn.setStyle("-fx-font-style: italic;");
        italicBtn.setOnAction(e -> applyRunStyle(r -> r.setItalic(italicBtn.isSelected())));

        ToggleButton underlineBtn = new ToggleButton("U");
        underlineBtn.setOnAction(e -> applyRunStyle(r -> r.setUnderline(underlineBtn.isSelected())));

        ToggleButton strikeBtn = new ToggleButton("S");
        strikeBtn.setStyle("-fx-strikethrough: true;");
        strikeBtn.setOnAction(e -> applyRunStyle(r -> r.setStrikethrough(strikeBtn.isSelected())));

        // Colors
        ColorPicker textColor = new ColorPicker(Color.BLACK);
        textColor.setPrefWidth(40);
        textColor.setOnAction(e -> applyRunStyle(r -> r.setTextColor(textColor.getValue())));

        ColorPicker highlightColor = new ColorPicker(Color.TRANSPARENT);
        highlightColor.setPrefWidth(40);
        highlightColor.setOnAction(e -> applyRunStyle(r -> r.setHighlightColor(highlightColor.getValue())));

        // Alignments
        Button alignLeft = new Button("Left");
        alignLeft.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.LEFT));

        Button alignCenter = new Button("Center");
        alignCenter.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.CENTER));

        Button alignRight = new Button("Right");
        alignRight.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.RIGHT));

        Button alignJustify = new Button("Justify");
        alignJustify.setOnAction(e -> setParagraphAlignment(Paragraph.Alignment.JUSTIFY));

        // Lists
        Button bulletBtn = new Button("• List");
        bulletBtn.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.BULLET ? Paragraph.ListType.NONE : Paragraph.ListType.BULLET);
                document.setModified(true);
                relayout();
            }
        });

        Button numberBtn = new Button("1. List");
        numberBtn.setOnAction(e -> {
            Paragraph p = getActiveParagraph();
            if (p != null) {
                p.setListType(p.getListType() == Paragraph.ListType.NUMBERED ? Paragraph.ListType.NONE : Paragraph.ListType.NUMBERED);
                document.setModified(true);
                relayout();
            }
        });

        // Insert Table / Page Break
        Button insertTableBtn = new Button("+ Table");
        insertTableBtn.setOnAction(e -> {
            DocumentTable table = new DocumentTable(3, 3);
            document.addElement(table);
            document.addElement(new Paragraph());
            relayout();
        });

        Button insertPageBreakBtn = new Button("Page Break");
        insertPageBreakBtn.setOnAction(e -> {
            document.addElement(new PageBreakElement());
            document.addElement(new Paragraph());
            relayout();
        });

        ribbonBar.getChildren().addAll(
                styleCombo, fontCombo, sizeCombo, boldBtn, italicBtn, underlineBtn, strikeBtn,
                textColor, highlightColor, new Separator(Orientation.VERTICAL),
                alignLeft, alignCenter, alignRight, alignJustify, new Separator(Orientation.VERTICAL),
                bulletBtn, numberBtn, new Separator(Orientation.VERTICAL),
                insertTableBtn, insertPageBreakBtn
        );
    }

    private void applyRunStyle(java.util.function.Consumer<TextRun> modifier) {
        Paragraph p = getActiveParagraph();
        if (p != null && !p.getRuns().isEmpty()) {
            TextRun r = p.getRuns().get(Math.min(currentRunIndex, p.getRuns().size() - 1));
            modifier.accept(r);
            document.setModified(true);
            relayout();
        }
    }

    private void setParagraphAlignment(Paragraph.Alignment align) {
        Paragraph p = getActiveParagraph();
        if (p != null) {
            p.setAlignment(align);
            document.setModified(true);
            relayout();
        }
    }

    private Paragraph getActiveParagraph() {
        if (currentElementIndex >= 0 && currentElementIndex < document.getElements().size()) {
            DocumentElement el = document.getElements().get(currentElementIndex);
            if (el instanceof Paragraph p) return p;
        }
        for (DocumentElement el : document.getElements()) {
            if (el instanceof Paragraph p) return p;
        }
        return null;
    }

    public void relayout() {
        pages.clear();
        pages.addAll(LayoutEngine.layout(document));

        // Adjust canvas dimensions to contain all pages with margins
        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double totalH = pages.size() * (pageH + gap) + gap;

        canvas.setWidth(pageW + 48.0);
        canvas.setHeight(totalH);

        updateStatus();
        render();
    }

    public void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double pageW = document.getPageSetup().getPageWidth();
        double pageH = document.getPageSetup().getPageHeight();
        double gap = 24.0;
        double pageX = (canvas.getWidth() - pageW) / 2.0;
        double currentY = gap;

        for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
            LayoutPage page = pages.get(pIdx);

            // 1. Draw page paper with drop shadow
            gc.setFill(Color.rgb(0, 0, 0, 0.15));
            gc.fillRect(pageX + 4, currentY + 4, pageW, pageH);

            gc.setFill(Color.WHITE);
            gc.fillRect(pageX, currentY, pageW, pageH);

            gc.setStroke(Color.rgb(215, 215, 215));
            gc.strokeRect(pageX, currentY, pageW, pageH);

            // 2. Draw margin guides (subtle)
            PageSetup setup = page.getPageSetup();
            gc.setStroke(Color.rgb(240, 240, 240));
            gc.strokeRect(pageX + setup.getMarginLeft(), currentY + setup.getMarginTop(),
                    setup.getPrintableWidth(), setup.getPrintableHeight());

            // 3. Draw Header & Footer
            if (page.getHeaderLine() != null) {
                drawLine(gc, page.getHeaderLine(), pageX, currentY);
            }
            if (page.getFooterLine() != null) {
                drawLine(gc, page.getFooterLine(), pageX, currentY);
            }

            // 4. Draw Lines
            for (LayoutLine line : page.getLines()) {
                drawLine(gc, line, pageX, currentY);
            }

            // 5. Draw Tables
            for (LayoutPage.TableBox tBox : page.getTableBoxes()) {
                drawTableBox(gc, tBox, pageX, currentY);
            }

            // 6. Draw Images
            for (LayoutPage.ImageBox imgBox : page.getImageBoxes()) {
                if (imgBox.image != null) {
                    gc.drawImage(imgBox.image, pageX + imgBox.x, currentY + imgBox.y, imgBox.width, imgBox.height);
                }
            }

            currentY += pageH + gap;
        }

        // 7. Draw Caret
        if (caretVisible) {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1.5);
            gc.strokeLine(caretX, caretY, caretX, caretY + caretHeight);
        }
    }

    private void drawLine(GraphicsContext gc, LayoutLine line, double pageX, double pageY) {
        gc.setTextBaseline(VPos.BASELINE);
        for (LayoutLine.LineSegment seg : line.getSegments()) {
            gc.setFont(seg.getFont());

            // Highlight background
            if (seg.getRun().getHighlightColor() != null) {
                gc.setFill(seg.getRun().getHighlightColor());
                gc.fillRect(pageX + line.getX() + seg.getX(), pageY + line.getY(), seg.getWidth(), line.getHeight());
            }

            // Text
            gc.setFill(seg.getRun().getTextColor());
            gc.fillText(seg.getText(), pageX + line.getX() + seg.getX(), pageY + line.getY() + line.getBaseline());

            // Underline / Strikethrough
            if (seg.getRun().isUnderline()) {
                gc.setStroke(seg.getRun().getTextColor());
                gc.setLineWidth(1.0);
                double ulY = pageY + line.getY() + line.getBaseline() + 2;
                gc.strokeLine(pageX + line.getX() + seg.getX(), ulY, pageX + line.getX() + seg.getX() + seg.getWidth(), ulY);
            }
            if (seg.getRun().isStrikethrough()) {
                gc.setStroke(seg.getRun().getTextColor());
                gc.setLineWidth(1.0);
                double stY = pageY + line.getY() + (line.getHeight() / 2.0);
                gc.strokeLine(pageX + line.getX() + seg.getX(), stY, pageX + line.getX() + seg.getX() + seg.getWidth(), stY);
            }
        }
    }

    private void drawTableBox(GraphicsContext gc, LayoutPage.TableBox tBox, double pageX, double pageY) {
        gc.setStroke(tBox.borderColor);
        gc.setLineWidth(tBox.borderWidth);

        for (LayoutPage.TableCellBox cBox : tBox.cells) {
            double cx = pageX + cBox.x;
            double cy = pageY + cBox.y;

            if (cBox.cell.getBackgroundColor() != null) {
                gc.setFill(cBox.cell.getBackgroundColor());
                gc.fillRect(cx, cy, cBox.width, cBox.height);
            }

            gc.strokeRect(cx, cy, cBox.width, cBox.height);

            for (LayoutLine line : cBox.lines) {
                drawLine(gc, line, pageX, pageY);
            }
        }
    }

    private void handleCanvasClick(MouseEvent e) {
        canvas.requestFocus();
        caretX = e.getX();
        caretY = e.getY();
        caretVisible = true;
        render();
    }

    private void handleKeyPressed(KeyEvent e) {
        Paragraph p = getActiveParagraph();
        if (p == null) return;

        if (e.getCode() == KeyCode.ENTER) {
            Paragraph newP = new Paragraph();
            document.addElement(newP);
            currentElementIndex = document.getElements().indexOf(newP);
            currentRunIndex = 0;
            currentCharOffset = 0;
            relayout();
            e.consume();
        } else if (e.getCode() == KeyCode.BACK_SPACE) {
            if (!p.getRuns().isEmpty()) {
                TextRun lastRun = p.getRuns().get(p.getRuns().size() - 1);
                String txt = lastRun.getText();
                if (!txt.isEmpty()) {
                    lastRun.setText(txt.substring(0, txt.length() - 1));
                    document.setModified(true);
                    relayout();
                }
            }
            e.consume();
        }
    }

    private void handleKeyTyped(KeyEvent e) {
        String ch = e.getCharacter();
        if (ch == null || ch.isEmpty() || ch.charAt(0) < 32) return;

        Paragraph p = getActiveParagraph();
        if (p == null) {
            p = new Paragraph();
            document.addElement(p);
        }

        if (p.getRuns().isEmpty()) {
            p.addRun(new TextRun(ch));
        } else {
            TextRun lastRun = p.getRuns().get(p.getRuns().size() - 1);
            lastRun.setText(lastRun.getText() + ch);
        }

        document.setModified(true);
        relayout();
    }

    private void updateStatus() {
        statusStatsLabel.setText(String.format("Pages: %d | Words: %d | Characters: %d",
                pages.size(), document.getWordCount(), document.getCharacterCount()));
    }

    @Override
    public DocumentModel getDocument() {
        return document;
    }

    @Override
    public Node getRibbonToolbar() {
        return ribbonBar;
    }

    @Override
    public Node getStatusBarNode() {
        return statusStatsLabel;
    }

    @Override
    public void close() {
        if (caretBlink != null) {
            caretBlink.stop();
        }
    }
}
